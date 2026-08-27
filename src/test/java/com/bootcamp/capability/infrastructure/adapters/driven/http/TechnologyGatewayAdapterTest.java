package com.bootcamp.capability.infrastructure.adapters.driven.http;

import com.bootcamp.capability.domain.exception.TechnologyValidationUnavailableException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del adaptador driven {@link TechnologyGatewayAdapter} usando
 * {@link MockWebServer} para simular el Technology_Service sin depender de Docker
 * ni del microservicio real.
 *
 * <p>Cubre: emisión del subconjunto de ids existentes, traducción de errores 5xx
 * y de errores de conexión a {@link TechnologyValidationUnavailableException}, y
 * el cortocircuito ante entrada vacía (sin llamada HTTP).
 *
 * <p>Requirements: 7.1, 7.2, 7.4
 */
class TechnologyGatewayAdapterTest {

    private MockWebServer server;
    private TechnologyGatewayAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        adapter = new TechnologyGatewayAdapter(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("Emite solo los ids existentes devueltos por el Technology_Service")
    void emitsOnlyExistingIds() throws InterruptedException {
        // Se solicitan 1,2,3 pero el servicio solo conoce 1 y 3.
        String body = "[{\"id\":1,\"name\":\"Java\",\"description\":\"JVM\"},"
                + "{\"id\":3,\"name\":\"Spring\",\"description\":\"Framework\"}]";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body));

        StepVerifier.create(adapter.findExistingTechnologyIds(List.of(1L, 2L, 3L)))
                .expectNext(1L, 3L)
                .verifyComplete();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/technologies?ids=1,2,3");
    }

    @Test
    @DisplayName("Un error 5xx se traduce a TechnologyValidationUnavailableException")
    void serverErrorMapsToUnavailableException() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.findExistingTechnologyIds(List.of(1L, 2L, 3L)))
                .expectError(TechnologyValidationUnavailableException.class)
                .verify();
    }

    @Test
    @DisplayName("Un error de conexion se traduce a TechnologyValidationUnavailableException")
    void connectionErrorMapsToUnavailableException() throws IOException {
        // Cerrar el servidor provoca un error de conexion en la llamada.
        server.shutdown();

        StepVerifier.create(adapter.findExistingTechnologyIds(List.of(1L, 2L, 3L)))
                .expectError(TechnologyValidationUnavailableException.class)
                .verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Entrada vacia retorna Flux.empty sin realizar llamada HTTP")
    void emptyInputReturnsEmptyWithoutHttpCall() {
        StepVerifier.create(adapter.findExistingTechnologyIds(List.of()))
                .verifyComplete();

        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("Entrada nula retorna Flux.empty sin realizar llamada HTTP")
    void nullInputReturnsEmptyWithoutHttpCall() {
        StepVerifier.create(adapter.findExistingTechnologyIds(null))
                .verifyComplete();

        assertThat(server.getRequestCount()).isZero();
    }
}
