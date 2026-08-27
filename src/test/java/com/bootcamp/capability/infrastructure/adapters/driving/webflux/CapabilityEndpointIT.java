package com.bootcamp.capability.infrastructure.adapters.driving.webflux;

import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityRepository;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityTechnologyRepository;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityRequest;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.ErrorResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración end-to-end del endpoint {@code POST /api/v1/capabilities}
 * usando {@link WebTestClient} contra el contexto COMPLETO de Spring Boot, una
 * base de datos MySQL real levantada con Testcontainers y un
 * {@link MockWebServer} que simula el Technology_Service.
 *
 * <p>Ejercita el flujo reactivo completo de la arquitectura hexagonal sin mocks
 * de componentes internos: {@code CapabilityRouter -> CapabilityHandler ->
 * CapabilityUseCase -> (CapabilityPersistenceAdapter -> MySQL) +
 * (TechnologyGatewayAdapter -> MockWebServer)}, junto con la traducción de
 * errores del {@code GlobalErrorWebExceptionHandler}.
 *
 * <p>Casos cubiertos:
 * <ul>
 *   <li>POST válido -&gt; {@code 201 Created} con {@link CapabilityResponse} (Req 1.3).</li>
 *   <li>Nombre duplicado -&gt; {@code 409 Conflict} (Req 2.3).</li>
 *   <li>Nombre vacío / descripción vacía -&gt; {@code 400 Bad Request} (Req 3.1, 4.1).</li>
 *   <li>Menos de 3 / más de 20 tecnologías -&gt; {@code 400 Bad Request} (Req 5.1, 5.2).</li>
 *   <li>Tecnologías repetidas -&gt; {@code 400 Bad Request} (Req 6.1).</li>
 *   <li>Tecnología inexistente -&gt; {@code 400 Bad Request} (Req 7.2).</li>
 *   <li>Technology_Service caído -&gt; {@code 502 Bad Gateway} (Req 7.4).</li>
 * </ul>
 *
 * <p><b>Requirements: 1.3, 2.3, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2, 6.1, 7.2, 7.4</b>
 *
 * <p>El contenedor MySQL y la URL del Technology_Service (apuntando al
 * {@link MockWebServer}) se enlazan al contexto reactivo mediante
 * {@link DynamicPropertySource}. La inicialización del esquema ({@code schema.sql})
 * la realiza Spring Boot al arrancar ({@code spring.sql.init.mode=always}).
 *
 * <p>Se usa un {@code GenericContainer<>("mysql:8.0")} (NO {@code MySQLContainer})
 * porque el proyecto es puramente R2DBC y no tiene el driver JDBC de MySQL en el
 * classpath de test; {@code MySQLContainer} verificaría el arranque abriendo una
 * conexión JDBC y fallaría con {@code ClassNotFoundException}.
 *
 * <p>Requiere Docker en ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient(timeout = "PT30S")
class CapabilityEndpointIT {

    private static final int MYSQL_PORT = 3306;
    private static final String DB_NAME = "capability_db";
    private static final String DB_USER = "test";
    private static final String DB_PASSWORD = "test";

    // MySQL real vía GenericContainer (el proyecto no tiene driver JDBC en test).
    private static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0")
            .withEnv("MYSQL_DATABASE", DB_NAME)
            .withEnv("MYSQL_USER", DB_USER)
            .withEnv("MYSQL_PASSWORD", DB_PASSWORD)
            .withEnv("MYSQL_ROOT_PASSWORD", "root")
            .withExposedPorts(MYSQL_PORT)
            .waitingFor(Wait.forLogMessage(".*port: 3306  MySQL Community Server.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(180)));

    // Simula el Technology_Service. Se arranca antes del contexto de Spring para
    // poder registrar su URL en las propiedades dinámicas.
    private static MockWebServer technologyService;

    @BeforeAll
    static void startInfrastructure() throws IOException {
        MYSQL.start();
        technologyService = new MockWebServer();
        technologyService.start();
    }

    @AfterAll
    static void stopInfrastructure() throws IOException {
        if (technologyService != null) {
            technologyService.shutdown();
        }
        MYSQL.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:mysql://%s:%d/%s",
                MYSQL.getHost(),
                MYSQL.getMappedPort(MYSQL_PORT),
                DB_NAME));
        registry.add("spring.r2dbc.username", () -> DB_USER);
        registry.add("spring.r2dbc.password", () -> DB_PASSWORD);
        // El WebClient del gateway apunta al MockWebServer.
        registry.add("technology.service.url", () -> technologyService.url("/").toString());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ICapabilityRepository capabilityRepository;

    @Autowired
    private ICapabilityTechnologyRepository capabilityTechnologyRepository;

    @BeforeEach
    void cleanState() {
        // Primero las asociaciones (FK hacia capability), luego las capacidades.
        capabilityTechnologyRepository.deleteAll().block();
        capabilityRepository.deleteAll().block();
        // Reinicia el dispatcher del MockWebServer para descartar cualquier
        // respuesta encolada por un test anterior cuya petición nunca llegó al
        // gateway (por ejemplo, tests que fallan una validación previa). Así cada
        // test parte de una cola de respuestas limpia y determinista.
        technologyService.setDispatcher(new QueueDispatcher());
    }

    /**
     * Encola en el MockWebServer una respuesta que declara existentes exactamente
     * los ids proporcionados (formato del Technology_Service: {@code [{id,name,description}]}).
     */
    private void enqueueExistingTechnologies(List<Long> ids) {
        String body = ids.stream()
                .map(id -> String.format(
                        "{\"id\":%d,\"name\":\"Tech%d\",\"description\":\"Desc%d\"}", id, id, id))
                .collect(Collectors.joining(",", "[", "]"));
        technologyService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body));
    }

    // --- 1. POST válido -> 201 Created con CapabilityResponse (Req 1.3) ---

    @Test
    void post_withValidData_returns201WithCapabilityResponse() {
        List<Long> techIds = List.of(1L, 2L, 3L);
        enqueueExistingTechnologies(techIds); // el servicio conoce todas las solicitadas

        CapabilityRequest request = new CapabilityRequest(
                "Backend Java", "Capacidad backend con Java", techIds);

        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(CapabilityResponse.class)
                .value(response -> {
                    assertThat(response.id()).isNotNull();
                    assertThat(response.id()).isPositive();
                    assertThat(response.name()).isEqualTo("Backend Java");
                    assertThat(response.description()).isEqualTo("Capacidad backend con Java");
                    assertThat(response.technologyIds())
                            .containsExactlyInAnyOrderElementsOf(techIds);
                });
    }

    // --- 2. Nombre duplicado -> 409 Conflict (Req 2.3) ---

    @Test
    void post_withDuplicateName_returns409() {
        List<Long> techIds = List.of(1L, 2L, 3L);

        enqueueExistingTechnologies(techIds);
        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("Cloud", "Capacidad cloud", techIds))
                .exchange()
                .expectStatus().isCreated();

        // Mismo nombre (distinta capitalización) debe rechazarse con 409.
        enqueueExistingTechnologies(techIds);
        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("cloud", "Otra descripción", techIds))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT.value());
                    assertThat(error.message()).isNotBlank();
                });
    }

    // --- 3. Nombre / descripción inválidos -> 400 (Req 3.1, 4.1) ---

    @Test
    void post_withEmptyName_returns400() {
        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("   ", "Descripción válida", List.of(1L, 2L, 3L)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void post_withEmptyDescription_returns400() {
        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("Datos", "   ", List.of(1L, 2L, 3L)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    // --- 4. Cantidad de tecnologías fuera de rango -> 400 (Req 5.1, 5.2) ---

    @Test
    void post_withFewerThan3Technologies_returns400() {
        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("Pocas", "Menos de 3 tecnologías", List.of(1L, 2L)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void post_withMoreThan20Technologies_returns400() {
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1L, 21L)
                .boxed()
                .collect(Collectors.toList());

        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("Muchas", "Más de 20 tecnologías", tooMany))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    // --- 5. Tecnologías repetidas -> 400 (Req 6.1) ---

    @Test
    void post_withDuplicatedTechnologyIds_returns400() {
        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("Repetidas", "Con ids repetidos", List.of(1L, 2L, 2L, 3L)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    // --- 6. Tecnología inexistente -> 400 (Req 7.2) ---

    @Test
    void post_withNonExistentTechnology_returns400() {
        // Se solicitan 1,2,3 pero el Technology_Service solo conoce 1 y 2 (subconjunto estricto).
        enqueueExistingTechnologies(List.of(1L, 2L));

        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("Inexistente", "Con tecnología inexistente", List.of(1L, 2L, 3L)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    // --- 7. Technology_Service caído -> 502 (Req 7.4) ---

    @Test
    void post_whenTechnologyServiceReturns500_returns502() {
        technologyService.enqueue(new MockResponse().setResponseCode(500));

        webTestClient.post()
                .uri("/api/v1/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CapabilityRequest("Caido", "Technology_Service con error", List.of(1L, 2L, 3L)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
                    assertThat(error.message()).isNotBlank();
                });
    }
}
