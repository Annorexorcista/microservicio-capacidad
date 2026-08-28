package com.bootcamp.capability.infrastructure.adapters.driving.webflux;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

/**
 * Smoke test de OpenAPI para el endpoint de listado. Verifica que el documento
 * {@code /v3/api-docs} incluya el path {@code GET /api/v1/capabilities} con sus
 * cuatro parámetros de consulta (page, size, sortBy, sortDirection).
 *
 * <p><b>Requirements: 9.1</b>
 *
 * <p>Arranca el contexto completo (springdoc-webflux genera la doc de las rutas
 * funcionales a partir de las anotaciones {@code @RouterOperation}). Requiere
 * MySQL vía Testcontainers para que el contexto R2DBC arranque, y Docker en
 * ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient(timeout = "PT30S")
class CapabilityListOpenApiIT {

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

    @BeforeAll
    static void start() {
        MYSQL.start();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:mysql://%s:%d/%s",
                MYSQL.getHost(), MYSQL.getMappedPort(MYSQL_PORT), DB_NAME));
        registry.add("spring.r2dbc.username", () -> DB_USER);
        registry.add("spring.r2dbc.password", () -> DB_PASSWORD);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("/v3/api-docs contiene GET /api/v1/capabilities con sus 4 parámetros")
    void apiDocsContainsListEndpoint() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/capabilities'].get").exists()
                .jsonPath("$.paths['/api/v1/capabilities'].get.parameters[?(@.name=='page')]").exists()
                .jsonPath("$.paths['/api/v1/capabilities'].get.parameters[?(@.name=='size')]").exists()
                .jsonPath("$.paths['/api/v1/capabilities'].get.parameters[?(@.name=='sortBy')]").exists()
                .jsonPath("$.paths['/api/v1/capabilities'].get.parameters[?(@.name=='sortDirection')]").exists();
    }
}
