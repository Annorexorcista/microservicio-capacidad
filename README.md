# Microservicio de Capacidades — HU2: Registrar Capacidades

Microservicio reactivo (Spring WebFlux + R2DBC/MySQL + WebClient) que expone el registro de capacidades siguiendo **arquitectura hexagonal** (puertos y adaptadores). Implementa la **Historia de Usuario 2 (HU2)**: registrar una capacidad con nombre, descripción y un conjunto de tecnologías asociadas, validando obligatoriedad, longitudes, cantidad y no repetición de tecnologías, unicidad del nombre y existencia de las tecnologías en el microservicio de Tecnología.

## Tabla de contenido

- [Funcionalidad](#funcionalidad)
- [Arquitectura hexagonal](#arquitectura-hexagonal)
- [Relación entre microservicios y WebClient](#relación-entre-microservicios-y-webclient)
- [Programación reactiva: Mono, Flux y operadores](#programación-reactiva-mono-flux-y-operadores)
- [Recorrido del flujo de una petición](#recorrido-del-flujo-de-una-petición)
- [Reglas de negocio](#reglas-de-negocio)
- [API REST](#api-rest)
- [Estrategia de pruebas](#estrategia-de-pruebas)
- [Cómo ejecutar](#cómo-ejecutar)

## Funcionalidad

El endpoint `POST /api/v1/capabilities` recibe un nombre, una descripción y una lista de `technologyIds`, y registra una capacidad nueva. Antes de persistir:

1. **Normaliza** nombre y descripción aplicando `trim`.
2. **Valida** obligatoriedad y longitud: nombre 1–50 caracteres, descripción 1–90 caracteres.
3. **Valida** la cantidad de tecnologías (entre 3 y 20) y que no haya identificadores repetidos.
4. **Verifica unicidad** del nombre, sin distinguir mayúsculas/minúsculas.
5. **Verifica la existencia** de todas las tecnologías consultando al microservicio de Tecnología vía `WebClient`.
6. **Persiste** la capacidad y sus asociaciones en MySQL vía R2DBC, de forma transaccional, y devuelve la capacidad creada con su `id` autogenerado.

Cualquier fallo se traduce a una respuesta HTTP uniforme: `400` (datos inválidos o tecnología inexistente), `409` (nombre duplicado), `502` (Technology_Service no disponible), `500` (error inesperado).

## Arquitectura hexagonal

El código separa el **dominio** (reglas de negocio puras, sin Spring) de la **infraestructura** (adaptadores que hablan con el mundo exterior). El dominio define **puertos** (interfaces) y la infraestructura provee **adaptadores** (implementaciones). Las dependencias apuntan siempre hacia el dominio.

```
src/main/java/com/bootcamp/capability
├── domain                        # Núcleo puro (sin anotaciones de framework)
│   ├── model/Capability              # Modelo de dominio inmutable
│   ├── api/ICapabilityServicePort    # Puerto de ENTRADA (lo consume la capa web)
│   ├── spi/ICapabilityPersistencePort# Puerto de SALIDA (persistencia)
│   ├── spi/ITechnologyGatewayPort    # Puerto de SALIDA (validación de tecnologías)
│   ├── usecase/CapabilityUseCase     # Reglas de negocio (implementa el puerto de entrada)
│   └── exception/                    # Errores de dominio + DomainErrorCode
├── application/config            # Cableado de beans (wiring) + R2DBC + WebClient + OpenAPI
└── infrastructure/adapters
    ├── driving/webflux           # Adaptador de ENTRADA (HTTP)
    │   ├── router/                   # RouterFunction (rutas funcionales)
    │   ├── handler/                  # Handler que compone el pipeline reactivo
    │   ├── dto/                      # Request/Response/Error (transporte)
    │   ├── mapper/                   # DTO <-> dominio
    │   └── exception/                # Handler global de errores reactivo
    └── driven
        ├── r2dbc                 # Adaptador de SALIDA (base de datos)
        │   ├── entity/               # Entidades @Table (capability, capability_technology)
        │   ├── repository/           # ReactiveCrudRepository
        │   ├── mapper/               # entidad <-> dominio
        │   └── adapter/              # Implementa ICapabilityPersistencePort
        └── http                  # Adaptador de SALIDA (gateway HTTP)
            ├── dto/                  # TechnologyGatewayResponse
            └── TechnologyGatewayAdapter # Implementa ITechnologyGatewayPort (WebClient)
```

Ventaja clave: el dominio (`CapabilityUseCase`, `Capability`, los puertos) **no conoce Spring, HTTP ni R2DBC**. Se prueba de forma unitaria con mocks, y los adaptadores se pueden sustituir sin tocar las reglas de negocio. El cableado se hace en `BeanConfiguration`, por eso las clases de dominio y los adaptadores no llevan `@Component`.

## Relación entre microservicios y WebClient

Las tecnologías son propiedad del **microservicio de Tecnología** y viven en su propia base de datos. Cumpliendo la regla del reto ("cada microservicio persiste únicamente su base de datos"), el microservicio de Capacidad **no** duplica el catálogo: persiste solo los `technologyId` en una tabla puente (`capability_technology`) y consulta al de Tecnología cuando necesita validar existencia.

Esa consulta se hace con **`WebClient`**, el cliente HTTP reactivo y no bloqueante de Spring (en lugar de `RestTemplate`, que es bloqueante). El microservicio de Tecnología expone `GET /api/v1/technologies?ids=1,2,3`, que devuelve solo las tecnologías existentes; comparando lo solicitado con lo devuelto, Capacidad detecta identificadores inexistentes. La `baseUrl` se configura por la variable `TECHNOLOGY_SERVICE_URL` (por defecto `http://localhost:8080`).

## Programación reactiva: Mono, Flux y operadores

Este servicio es **100% no bloqueante**. Nunca se usa `.block()` en el código de producción; todo se compone con operadores de **Project Reactor**. El registro combina dos fuentes de I/O —la base de datos (R2DBC) y una llamada HTTP saliente (`WebClient`)— en un único pipeline.

### Mono y Flux

- **`Mono<T>`**: flujo asíncrono que emite **0 o 1** elemento. El registro de una capacidad retorna `Mono<Capability>`.
- **`Flux<T>`**: flujo asíncrono que emite **0..N** elementos. La consulta de tecnologías existentes retorna `Flux<Long>` desde el gateway.

Nada se ejecuta hasta la **suscripción** (evaluación perezosa); en WebFlux el framework se suscribe al enviar la respuesta HTTP.

### Operadores usados en este proyecto

| Operador | Dónde | Para qué sirve |
|----------|-------|----------------|
| `flatMap` | `CapabilityUseCase`, `CapabilityHandler`, adaptadores | Encadena un paso que devuelve otro publisher (asíncrono): validación → unicidad → existencia → guardado. |
| `map` | `CapabilityHandler`, mappers, gateway | Transforma el valor con una función síncrona (entidad → dominio, dominio → DTO, response → id). |
| `collectList` | `CapabilityUseCase.ensureTechnologiesExist` | Recoge el `Flux<Long>` de tecnologías existentes en una lista para compararla con lo solicitado. |
| `Mono.just(x)` / `Mono.error(ex)` | `CapabilityUseCase` | Emite un valor disponible, o **falla** cortocircuitando el pipeline (así nunca se persiste tras un error). |
| `Flux.fromIterable` / `Flux.empty` | gateway, adaptador de persistencia | Construye flujos a partir de colecciones; `Flux.empty()` cortocircuita el gateway ante entrada vacía. |
| `concatMap` | `CapabilityPersistenceAdapter.save` | Inserta las filas de la tabla puente en orden, una por tecnología. |
| `onErrorMap(...)` | gateway y persistencia | Traduce errores: fallo del Technology_Service → `TechnologyValidationUnavailableException` (502); violación de UNIQUE → `CapabilityAlreadyExistsException` (409). |
| `.as(transactionalOperator::transactional)` | `CapabilityPersistenceAdapter.save` | Envuelve la escritura (capacidad + asociaciones) en una **transacción reactiva** atómica. |
| `bodyToMono` / `bodyToFlux` | `CapabilityHandler`, gateway | Deserializa cuerpos JSON de forma no bloqueante. |

### La "regla de oro" reactiva

Nunca bloquear. Todo se compone con operadores y WebFlux ejecuta el pipeline sobre un número reducido de hilos de event-loop, escalando mejor bajo carga que el modelo de un hilo por petición.

## Recorrido del flujo de una petición

`POST /api/v1/capabilities` con `{ "name": "  Backend  ", "description": "...", "technologyIds": [1,2,3] }`:

1. **`CapabilityRouter`** declara la ruta funcional (`RouterFunction`) y la delega en el handler. La documentación OpenAPI se declara con `@RouterOperation` porque las rutas funcionales no se auto-documentan como los `@RestController`.
2. **`CapabilityHandler.register`** compone el pipeline:
   `bodyToMono(CapabilityRequest) → map(toDomain) → flatMap(servicePort::registerCapability) → map(toResponse) → flatMap(ServerResponse 201)`.
3. **`CapabilityUseCase.registerCapability`** (dominio) ejecuta las reglas:
   `validate(...) → flatMap(ensureNameIsUnique) → flatMap(ensureTechnologiesExist) → flatMap(persistencePort::save)`.
   - `validate` aplica `trim`, comprueba obligatoriedad, longitudes, cantidad (3–20) y no repetición; ante fallo emite `Mono.error(InvalidCapabilityDataException)`.
   - `ensureNameIsUnique` consulta el puerto de persistencia; si el nombre existe emite `Mono.error(CapabilityAlreadyExistsException)`.
   - `ensureTechnologiesExist` consulta el gateway; si falta alguna tecnología emite `Mono.error(TechnologiesNotFoundException)`; si el servicio está caído se propaga `TechnologyValidationUnavailableException`.
4. **`CapabilityPersistenceAdapter.save`** guarda la capacidad (obtiene el id generado), inserta las filas de `capability_technology`, todo en una transacción reactiva; traduce violaciones de UNIQUE a `409`.
5. Si algo falla, el error viaja hasta **`GlobalErrorWebExceptionHandler`**, que lo traduce a un `ErrorResponse` JSON con el código HTTP adecuado.

## Reglas de negocio

| Regla | Detalle | Error / código HTTP |
|-------|---------|---------------------|
| Nombre obligatorio | No null/vacío/solo espacios (tras `trim`) | `NAME_REQUIRED` → 400 |
| Longitud del nombre | Máximo 50 caracteres (tras `trim`) | `NAME_TOO_LONG` → 400 |
| Descripción obligatoria | No null/vacía/solo espacios (tras `trim`) | `DESCRIPTION_REQUIRED` → 400 |
| Longitud de la descripción | Máximo 90 caracteres (tras `trim`) | `DESCRIPTION_TOO_LONG` → 400 |
| Cantidad mínima de tecnologías | Al menos 3 | `TECHNOLOGIES_TOO_FEW` → 400 |
| Cantidad máxima de tecnologías | Máximo 20 | `TECHNOLOGIES_TOO_MANY` → 400 |
| No repetición de tecnologías | Sin identificadores duplicados | `TECHNOLOGIES_DUPLICATED` → 400 |
| Unicidad del nombre | Case-insensitive; si ya existe se rechaza | `CapabilityAlreadyExistsException` → 409 |
| Existencia de tecnologías | Todas deben existir en el Technology_Service | `TechnologiesNotFoundException` → 400 |
| Disponibilidad del Technology_Service | Si no responde, no se puede validar | `TechnologyValidationUnavailableException` → 502 |
| No persistir ante error | Ninguna validación fallida debe escribir en BD | invariante verificada por tests |

El esquema (`schema.sql`) refuerza reglas en la BD: `name VARCHAR(50)`, `description VARCHAR(90)`, `CONSTRAINT uq_capability_name UNIQUE (name)`, y `capability_technology` con PK compuesta `(capability_id, technology_id)` (impide filas duplicadas) y FK a `capability`.

## API REST

### Registrar capacidad

`POST /api/v1/capabilities`

Request body:

```json
{
  "name": "Backend Java",
  "description": "Capacidad de desarrollo backend con Java",
  "technologyIds": [1, 2, 3]
}
```

Respuesta `201 Created`:

```json
{
  "id": 1,
  "name": "Backend Java",
  "description": "Capacidad de desarrollo backend con Java",
  "technologyIds": [1, 2, 3]
}
```

Respuesta de error (`400` / `409` / `502`):

```json
{
  "status": 409,
  "code": "CONFLICT",
  "message": "El nombre 'Backend Java' ya está registrado",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Documentación interactiva (Swagger UI): `http://localhost:8081/swagger-ui.html`.

## Estrategia de pruebas

El reto exige pruebas para cada regla de negocio. Se combinan tres niveles:

### 1. Tests unitarios del caso de uso — `CapabilityUseCaseTest`

JUnit 5 + Mockito + `StepVerifier`. Mockean **ambos** puertos SPI (persistencia y gateway) y verifican ejemplos y casos borde de cada regla: registro válido, límites de longitud (1/50, 1/90), cantidad de tecnologías (2/3/20/21), duplicados, nombre existente, tecnología inexistente, error del gateway y normalización por `trim`. Cada caso de rechazo verifica `verify(persistencePort, never()).save(any())` — nunca se persiste ante un error.

### 2. Property-based tests (jqwik) — `CapabilityUseCaseProperty1..10Test`

jqwik genera cientos de entradas aleatorias (mínimo 100 iteraciones por propiedad) y comprueba que una propiedad universal se cumple siempre. Ambos puertos SPI se mockean, aislando las reglas del dominio.

| Test | Propiedad |
|------|-----------|
| Property 1 | Un registro válido conserva los datos normalizados y persiste exactamente una vez. |
| Property 2 | Un nombre duplicado (case-insensitive) se rechaza y nunca persiste. |
| Property 3 | La normalización por `trim` es idempotente en los valores persistidos. |
| Property 4 | Nombre vacío/obligatorio se rechaza sin persistir. |
| Property 5 | Descripción vacía/obligatoria se rechaza sin persistir. |
| Property 6 | Nombre > 50 o descripción > 90 se rechazan sin persistir. |
| Property 7 | Cantidad de tecnologías fuera de rango (< 3 o > 20) se rechaza sin persistir. |
| Property 8 | Tecnologías repetidas se rechazan sin persistir. |
| Property 9 | Una tecnología inexistente rechaza el registro sin persistir. |
| Property 10 | Invariante global: ante cualquier error, `save` no se invoca jamás. |

### 3. Tests de integración (Testcontainers) — persistencia, gateway y endpoint

- **`CapabilityPersistenceAdapterIT`**: MySQL real en Docker (Testcontainers). Verifica que `save` asigna id y persiste la capacidad y sus asociaciones, que `existsByNameIgnoreCase` ignora mayúsculas/minúsculas, y que un insert duplicado se traduce a `CapabilityAlreadyExistsException`.
- **`TechnologyGatewayAdapterTest`**: usa `MockWebServer` (OkHttp) para simular el Technology_Service sin depender de Docker ni del microservicio real. Verifica que emite solo los ids existentes, que un 5xx o un error de conexión se traducen a `TechnologyValidationUnavailableException`, y que una entrada vacía no realiza llamada HTTP.
- **`CapabilityEndpointIT`**: arranca el contexto completo de Spring Boot y usa `WebTestClient` contra MySQL real (Testcontainers) y `MockWebServer` (Technology_Service). Ejercita el endpoint real: `201`, `409`, `400` (nombre/descripción, cantidad, repetidos, inexistentes) y `502`.

#### Nota técnica: MySQL con Testcontainers en un proyecto solo-R2DBC

Este proyecto no incluye el driver JDBC de MySQL (es puramente R2DBC). `MySQLContainer` comprueba el arranque abriendo una conexión JDBC, lo que provocaría `ClassNotFoundException`. Por eso los tests de integración levantan MySQL con un `GenericContainer<>("mysql:8.0")` y una wait strategy basada en el log de arranque:

```java
new GenericContainer<>("mysql:8.0")
    .withEnv("MYSQL_DATABASE", "capability_db")
    .withEnv("MYSQL_USER", "test")
    .withEnv("MYSQL_PASSWORD", "test")
    .withEnv("MYSQL_ROOT_PASSWORD", "root")
    .withExposedPorts(3306)
    .waitingFor(Wait.forLogMessage(".*port: 3306  MySQL Community Server.*", 1)
        .withStartupTimeout(Duration.ofSeconds(180)));
```

La conexión de la aplicación sigue siendo R2DBC (`spring.r2dbc.*`), enlazada al contenedor mediante `@DynamicPropertySource`.

> Los tests de integración requieren **Docker en ejecución**.

## Cómo ejecutar

Requisitos: JDK 17, Docker (solo para los tests de integración), un MySQL accesible y el microservicio de Tecnología en ejecución para el flujo real.

Ejecutar la suite de pruebas completa:

```bash
./gradlew clean test
```

Levantar el servicio (variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `TECHNOLOGY_SERVICE_URL`; por defecto BD `localhost:3306/capability_db` y Technology_Service `http://localhost:8080`):

```bash
./gradlew bootRun
```

El esquema `schema.sql` se ejecuta automáticamente al arrancar (`spring.sql.init.mode=always`).

Endpoints útiles (el servicio escucha en el puerto **8081**):

- API: `POST http://localhost:8081/api/v1/capabilities`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

---

**Estado:** HU2 (Registrar Capacidades) completa — dominio, persistencia, gateway y endpoint implementados y cubiertos por tests unitarios, property-based (jqwik, 10 propiedades) e integración (Testcontainers + MockWebServer).
