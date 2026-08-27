# Requirements Document

## Introduction

Este documento define los requerimientos para la HU 2 - Registrar capacidades del microservicio de Capacidad, un servicio reactivo construido con Spring WebFlux y R2DBC sobre MySQL bajo arquitectura hexagonal. La funcionalidad permite a un administrador registrar capacidades que agrupan tecnologías. Cada capacidad se compone de un identificador, un nombre único, una descripción obligatoria y un conjunto de tecnologías asociadas (entre 3 y 20, sin repetidos). Las tecnologías NO se almacenan en este microservicio: viven en el microservicio de Tecnología. Capacidad persiste únicamente los identificadores de tecnología y valida su existencia consultando al microservicio de Tecnología de forma reactiva mediante `WebClient`. El registro se expone mediante un endpoint REST de tipo POST que valida los datos de entrada, valida la existencia de las tecnologías, persiste la capacidad y su asociación con las tecnologías de forma no bloqueante y transaccional, y retorna la capacidad creada o los errores correspondientes.

## Glossary

- **Capacidad**: Entidad de negocio compuesta por un identificador, un nombre, una descripción y un conjunto de identificadores de tecnología asociados, que agrupa tecnologías del bootcamp.
- **Capability_Service**: Microservicio reactivo responsable de gestionar el ciclo de vida de las capacidades.
- **Capability_API**: Componente de la capa driving (WebFlux) que expone el endpoint REST de registro de capacidades.
- **Capability_UseCase**: Componente de la capa de dominio que orquesta las reglas de negocio para el registro de una capacidad.
- **Capability_Repository**: Adaptador driven que persiste capacidades de forma reactiva mediante R2DBC sobre MySQL.
- **Technology_Gateway**: Adaptador driven que consulta el microservicio de Tecnología mediante `WebClient` para verificar la existencia de tecnologías por sus identificadores.
- **Technology_Service**: Microservicio externo que gestiona las tecnologías; expone `GET /api/v1/technologies?ids=1,2,3` para consultar tecnologías por identificadores.
- **Admin**: Usuario con rol administrador autorizado para registrar capacidades.
- **Nombre**: Atributo de texto que identifica de forma única a una capacidad, con longitud máxima de 50 caracteres.
- **Descripcion**: Atributo de texto obligatorio que describe una capacidad, con longitud máxima de 90 caracteres.
- **TechnologyId**: Identificador numérico de una tecnología existente en el Technology_Service.

## Requirements

### Requirement 1: Registrar una capacidad válida

**User Story:** Como admin, quiero registrar una capacidad con nombre, descripción y un conjunto de tecnologías asociadas, para agrupar tecnologías del bootcamp.

#### Acceptance Criteria

1. WHEN el Admin envía una solicitud de registro con un Nombre entre 1 y 50 caracteres, una Descripcion entre 1 y 90 caracteres y un conjunto de entre 3 y 20 TechnologyId sin repetidos y existentes, THE Capability_UseCase SHALL crear una Capacidad con un identificador generado, el Nombre y la Descripcion proporcionados y las asociaciones a las tecnologías.
2. WHEN una Capacidad es creada exitosamente, THE Capability_Repository SHALL persistir la Capacidad y sus asociaciones con las tecnologías en la base de datos MySQL de forma no bloqueante y dentro de una misma transacción.
3. WHEN una Capacidad es persistida exitosamente, THE Capability_API SHALL retornar la Capacidad creada con su identificador, Nombre, Descripcion y el listado de TechnologyId asociados, con el código de estado HTTP 201.

### Requirement 2: Validar unicidad del nombre

**User Story:** Como admin, quiero que el nombre de la capacidad no se pueda repetir, para evitar capacidades duplicadas en el catálogo del bootcamp.

#### Acceptance Criteria

1. WHEN el Admin envía una solicitud de registro con un Nombre, THE Capability_UseCase SHALL comparar el Nombre contra los Nombres existentes usando una comparación sin distinción entre mayúsculas y minúsculas y con los espacios en blanco iniciales y finales eliminados, para determinar si ya existe una Capacidad con el mismo Nombre normalizado.
2. IF ya existe una Capacidad con el mismo Nombre normalizado, THEN THE Capability_UseCase SHALL rechazar el registro sin persistir ningún dato de la solicitud.
3. IF el registro es rechazado por Nombre duplicado, THEN THE Capability_API SHALL retornar una respuesta con el código de estado HTTP 409 y un mensaje que indique que el Nombre ya está registrado.
4. WHEN el Admin envía una solicitud de registro con un Nombre que no coincide con ningún Nombre normalizado existente, THE Capability_UseCase SHALL continuar el proceso de registro.

### Requirement 3: Validar presencia y longitud del nombre

**User Story:** Como admin, quiero que el sistema valide el nombre de la capacidad, para garantizar que los datos registrados sean consistentes y completos.

#### Acceptance Criteria

1. IF el Nombre está ausente, es nulo, es una cadena vacía o contiene únicamente espacios en blanco, THEN THE Capability_UseCase SHALL rechazar el registro y THE Capability_API SHALL retornar un error con el código de estado HTTP 400 y un mensaje que indique que el Nombre es obligatorio.
2. IF el Nombre, después de eliminar los espacios en blanco iniciales y finales, tiene una longitud mayor a 50 caracteres, THEN THE Capability_UseCase SHALL rechazar el registro y THE Capability_API SHALL retornar un error con el código de estado HTTP 400 y un mensaje que indique que el Nombre excede la longitud máxima de 50 caracteres.
3. WHEN el Nombre, después de eliminar los espacios en blanco iniciales y finales, tiene una longitud entre 1 y 50 caracteres inclusive, THE Capability_UseCase SHALL aceptar el Nombre como válido para continuar el proceso de registro.
4. IF el registro es rechazado por una validación del Nombre, THEN THE Capability_UseCase SHALL abortar el registro sin persistir ningún dato de la capacidad en la base de datos.

### Requirement 4: Validar presencia y longitud de la descripción

**User Story:** Como admin, quiero que toda capacidad tenga una descripción con longitud controlada, para que cada capacidad quede correctamente documentada.

#### Acceptance Criteria

1. IF la Descripcion está ausente, es nula o contiene únicamente caracteres de espacio en blanco, THEN THE Capability_UseCase SHALL rechazar el registro sin persistir ningún dato y THE Capability_API SHALL retornar el código de estado HTTP 400 con un mensaje que indique que la Descripcion es obligatoria.
2. IF la Descripcion, después de eliminar los espacios en blanco iniciales y finales, tiene una longitud mayor a 90 caracteres, THEN THE Capability_UseCase SHALL rechazar el registro sin persistir ningún dato y THE Capability_API SHALL retornar el código de estado HTTP 400 con un mensaje que indique que la Descripcion excede la longitud máxima de 90 caracteres.
3. WHEN la Descripcion está presente y tiene una longitud entre 1 y 90 caracteres inclusive, tras eliminar los espacios en blanco iniciales y finales, THE Capability_UseCase SHALL aceptar la Descripcion como válida y continuar con el registro.
4. WHERE la Descripcion contiene espacios en blanco iniciales o finales, THE Capability_UseCase SHALL eliminar dichos espacios antes de validar la longitud y persistir el valor resultante.

### Requirement 5: Validar la cantidad de tecnologías asociadas

**User Story:** Como admin, quiero que cada capacidad agrupe entre 3 y 20 tecnologías, para que las capacidades tengan una composición mínima significativa y no excedan un límite manejable.

#### Acceptance Criteria

1. IF el conjunto de TechnologyId asociados tiene menos de 3 elementos (contando solo identificadores distintos), THEN THE Capability_UseCase SHALL rechazar el registro sin persistir ningún dato y THE Capability_API SHALL retornar el código de estado HTTP 400 con un mensaje que indique que una capacidad debe tener como mínimo 3 tecnologías.
2. IF el conjunto de TechnologyId asociados tiene más de 20 elementos distintos, THEN THE Capability_UseCase SHALL rechazar el registro sin persistir ningún dato y THE Capability_API SHALL retornar el código de estado HTTP 400 con un mensaje que indique que una capacidad debe tener como máximo 20 tecnologías.
3. WHEN el conjunto de TechnologyId asociados tiene entre 3 y 20 elementos distintos inclusive, THE Capability_UseCase SHALL aceptar la cantidad como válida y continuar con el registro.

### Requirement 6: Rechazar tecnologías repetidas

**User Story:** Como admin, quiero que una capacidad no tenga tecnologías repetidas, para que las asociaciones sean consistentes y el conteo de tecnologías sea correcto.

#### Acceptance Criteria

1. IF el conjunto de TechnologyId asociados contiene al menos un identificador repetido, THEN THE Capability_UseCase SHALL rechazar el registro sin persistir ningún dato y THE Capability_API SHALL retornar el código de estado HTTP 400 con un mensaje que indique que no se permiten tecnologías repetidas.
2. WHEN todos los TechnologyId asociados son distintos entre sí, THE Capability_UseCase SHALL aceptar el conjunto como válido para continuar el proceso de registro.

### Requirement 7: Validar la existencia de las tecnologías asociadas

**User Story:** Como admin, quiero que las tecnologías asociadas a una capacidad existan realmente en el catálogo de tecnologías, para evitar asociaciones a tecnologías inexistentes.

#### Acceptance Criteria

1. WHEN el conjunto de TechnologyId asociados pasa las validaciones de cantidad y no repetición, THE Capability_UseCase SHALL consultar al Technology_Service, mediante el Technology_Gateway, cuáles de esos identificadores corresponden a tecnologías existentes.
2. IF al menos uno de los TechnologyId asociados no corresponde a una tecnología existente, THEN THE Capability_UseCase SHALL rechazar el registro sin persistir ningún dato y THE Capability_API SHALL retornar el código de estado HTTP 400 con un mensaje que indique cuáles identificadores no existen.
3. WHEN todos los TechnologyId asociados corresponden a tecnologías existentes, THE Capability_UseCase SHALL continuar con la persistencia de la capacidad.
4. IF el Technology_Service no está disponible o responde con un error, THEN THE Capability_API SHALL retornar un código de estado HTTP 502 con un mensaje que indique que no fue posible validar las tecnologías.

### Requirement 8: Procesamiento reactivo no bloqueante

**User Story:** Como equipo de desarrollo, quiero que el registro de capacidades se procese de forma reactiva, para cumplir con la arquitectura no bloqueante del microservicio.

#### Acceptance Criteria

1. THE Capability_Service SHALL procesar la solicitud de registro de forma no bloqueante utilizando tipos reactivos Mono o Flux.
2. THE Capability_Service SHALL completar la operación de registro sin utilizar llamadas bloqueantes.
3. THE Technology_Gateway SHALL consultar el Technology_Service de forma no bloqueante mediante `WebClient`.

### Requirement 9: Documentación OpenAPI

**User Story:** Como consumidor del microservicio, quiero disponer de documentación OpenAPI del endpoint de registro, para conocer el contrato de la API sin inspeccionar el código.

#### Acceptance Criteria

1. THE Capability_Service SHALL exponer documentación OpenAPI que describa el endpoint de registro de capacidades, incluyendo el esquema de la solicitud, el esquema de la respuesta y los códigos de estado 201, 400, 409 y 502.
