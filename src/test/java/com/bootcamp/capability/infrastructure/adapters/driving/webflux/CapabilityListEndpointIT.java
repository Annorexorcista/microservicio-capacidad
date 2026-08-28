package com.bootcamp.capability.infrastructure.adapters.driving.webflux;

import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityEntity;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityTechnologyEntity;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityRepository;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityTechnologyRepository;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityPageResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.ErrorResponse;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración end-to-end del endpoint {@code GET /api/v1/capabilities}
 * usando {@link WebTestClient} contra el contexto completo de Spring Boot, MySQL
 * real (Testcontainers) y un {@link MockWebServer} que simula el Technology_Service.
 *
 * <p>Casos cubiertos:
 * <ul>
 *   <li>GET válido -&gt; 200 con {@link CapabilityPageResponse} y tecnologías
 *       enriquecidas (id+name) (Req 1.3, 5.4);</li>
 *   <li>orden por name y por technologyCount asc/desc (Req 2.1, 2.2, 3.1, 3.2);</li>
 *   <li>página fuera de rango -&gt; 200 con content vacío (Req 1.4);</li>
 *   <li>sortBy/sortDirection/page/size inválidos -&gt; 400 (Req 2.4, 3.4, 4.3, 4.4, 4.5);</li>
 *   <li>Technology_Service caído (5xx) -&gt; 502 (Req 7.1).</li>
 * </ul>
 *
 * <p><b>Requirements: 1.3, 1.4, 2.1, 2.2, 2.4, 3.1, 3.2, 3.4, 4.3, 4.4, 4.5, 5.4, 7.1</b>
 *
 * <p>Se usa un {@code GenericContainer<>("mysql:8.0")} (NO {@code MySQLContainer}).
 * Requiere Docker en ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient(timeout = "PT30S")
class CapabilityListEndpointIT {

    private static final int MYSQL_PORT = 3306;
    private static final String DB_NAME = "capability_db";
    private static final String DB_USER = "test";
    private static final String DB_PASSWORD = "test";

    private static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0")
            .withEnv("MYSQL_DATABASE", DB_NAME)
            .withEnv("MYSQL_USER", DB_USER)
            .withEnv("MYSQL_PASSWORD", DB_PASSWORD)
            .withEnv("MYSQL_ROOT_PASSWORD", "root")
            .withExposedPorts(MYSQL_PORT)
            .waitingFor(Wait.forLogMessage(".*port: 3306  MySQL Community Server.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(180)));

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
                MYSQL.getHost(), MYSQL.getMappedPort(MYSQL_PORT), DB_NAME));
        registry.add("spring.r2dbc.username", () -> DB_USER);
        registry.add("spring.r2dbc.password", () -> DB_PASSWORD);
        registry.add("technology.service.url", () -> technologyService.url("/").toString());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ICapabilityRepository capabilityRepository;

    @Autowired
    private ICapabilityTechnologyRepository capabilityTechnologyRepository;

    @Autowired
    private R2dbcEntityTemplate entityTemplate;

    private Long alfaId;
    private Long betaId;

    @BeforeEach
    void seed() {
        capabilityTechnologyRepository.deleteAll().block();
        capabilityRepository.deleteAll().block();

        // Beta(1,2,3), Alfa(1), Gamma(1,2)
        betaId = capabilityRepository.save(new CapabilityEntity(null, "Beta", "d"))
                .map(CapabilityEntity::getId).block();
        insertTech(betaId, 1L, 2L, 3L);
        alfaId = capabilityRepository.save(new CapabilityEntity(null, "Alfa", "d"))
                .map(CapabilityEntity::getId).block();
        insertTech(alfaId, 1L);
        Long gamma = capabilityRepository.save(new CapabilityEntity(null, "Gamma", "d"))
                .map(CapabilityEntity::getId).block();
        insertTech(gamma, 1L, 2L);
    }

    private void insertTech(Long capabilityId, Long... techIds) {
        for (Long techId : techIds) {
            entityTemplate.insert(new CapabilityTechnologyEntity(capabilityId, techId)).block();
        }
    }

    /**
     * Dispatcher que responde a cualquier consulta {@code GET /api/v1/technologies?ids=...}
     * devolviendo, por cada id solicitado, {@code {id,name:"TechN",description}}.
     */
    private void dispatchTechnologiesEcho() {
        technologyService.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                int idx = path.indexOf("ids=");
                String body = "[]";
                if (idx >= 0) {
                    String csv = path.substring(idx + 4);
                    String[] ids = csv.split(",");
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < ids.length; i++) {
                        if (i > 0) {
                            sb.append(",");
                        }
                        String id = ids[i].trim();
                        sb.append(String.format(
                                "{\"id\":%s,\"name\":\"Tech%s\",\"description\":\"d\"}", id, id));
                    }
                    sb.append("]");
                    body = sb.toString();
                }
                return new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody(body);
            }
        });
    }

    // --- GET válido -> 200 con enriquecimiento (Req 1.3, 5.4) ---

    @Test
    @DisplayName("GET válido por name ASC -> 200 con página y tecnologías enriquecidas")
    void getValid_returns200WithEnrichedTechnologies() {
        dispatchTechnologiesEcho();

        webTestClient.get()
                .uri("/api/v1/capabilities?page=0&size=10&sortBy=name&sortDirection=asc")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CapabilityPageResponse.class)
                .value(resp -> {
                    assertThat(resp.page()).isZero();
                    assertThat(resp.size()).isEqualTo(10);
                    assertThat(resp.totalElements()).isEqualTo(3L);
                    assertThat(resp.totalPages()).isEqualTo(1);
                    assertThat(resp.content()).extracting("name")
                            .containsExactly("Alfa", "Beta", "Gamma");
                    // Alfa tiene tech 1 con name Tech1
                    var alfa = resp.content().get(0);
                    assertThat(alfa.technologies()).extracting("id").containsExactly(1L);
                    assertThat(alfa.technologies()).extracting("name").containsExactly("Tech1");
                    // Beta tiene 3 tecnologías
                    assertThat(resp.content().get(1).technologies()).hasSize(3);
                });
    }

    @Test
    @DisplayName("GET por technologyCount DESC -> 200 con Beta(3) primero")
    void getByTechnologyCountDesc_returns200Ordered() {
        dispatchTechnologiesEcho();

        webTestClient.get()
                .uri("/api/v1/capabilities?sortBy=technologyCount&sortDirection=desc")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CapabilityPageResponse.class)
                .value(resp -> assertThat(resp.content()).extracting("name")
                        .containsExactly("Beta", "Gamma", "Alfa"));
    }

    // --- página fuera de rango -> 200 con content vacío (Req 1.4) ---

    @Test
    @DisplayName("Página fuera de rango -> 200 con content vacío y metadata coherente")
    void pageOutOfRange_returns200Empty() {
        dispatchTechnologiesEcho();

        webTestClient.get()
                .uri("/api/v1/capabilities?page=5&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CapabilityPageResponse.class)
                .value(resp -> {
                    assertThat(resp.content()).isEmpty();
                    assertThat(resp.totalElements()).isEqualTo(3L);
                    assertThat(resp.totalPages()).isEqualTo(1);
                });
    }

    // --- parámetros inválidos -> 400 (Req 2.4, 3.4, 4.3, 4.4, 4.5) ---

    @Test
    @DisplayName("sortBy inválido -> 400")
    void invalidSortBy_returns400() {
        webTestClient.get()
                .uri("/api/v1/capabilities?sortBy=unknown")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(err -> assertThat(err.status()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    @DisplayName("sortDirection inválido -> 400")
    void invalidSortDirection_returns400() {
        webTestClient.get()
                .uri("/api/v1/capabilities?sortDirection=sideways")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("page negativo -> 400")
    void negativePage_returns400() {
        webTestClient.get()
                .uri("/api/v1/capabilities?page=-1")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("size mayor a 100 -> 400")
    void sizeTooLarge_returns400() {
        webTestClient.get()
                .uri("/api/v1/capabilities?size=101")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("size menor a 1 -> 400")
    void sizeTooSmall_returns400() {
        webTestClient.get()
                .uri("/api/v1/capabilities?size=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    // --- Technology_Service caído -> 502 (Req 7.1) ---

    @Test
    @DisplayName("Technology_Service responde 500 -> 502")
    void technologyServiceDown_returns502() {
        technologyService.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(500);
            }
        });

        webTestClient.get()
                .uri("/api/v1/capabilities?page=0&size=10")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody(ErrorResponse.class)
                .value(err -> assertThat(err.status()).isEqualTo(HttpStatus.BAD_GATEWAY.value()));
    }

    // --- GET ?ids= : consulta por identificadores (consumo entre microservicios) ---

    @Test
    @DisplayName("GET ?ids= -> 200 con la lista de capacidades solicitadas y sus tecnologías")
    void getByIds_returns200WithRequestedCapabilities() {
        dispatchTechnologiesEcho();

        // Se piden todas por id; se devuelven solo esas, cada una con sus tecnologías id+name.
        webTestClient.get()
                .uri("/api/v1/capabilities?ids=" + alfaId + "," + betaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[?(@.name=='Alfa')].technologies.length()").isEqualTo(1)
                .jsonPath("$[?(@.name=='Beta')].technologies.length()").isEqualTo(3);
    }
}
