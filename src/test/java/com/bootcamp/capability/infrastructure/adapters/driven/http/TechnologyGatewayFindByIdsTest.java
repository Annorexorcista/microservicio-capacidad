package com.bootcamp.capability.infrastructure.adapters.driven.http;

import com.bootcamp.capability.domain.exception.TechnologyValidationUnavailableException;
import com.bootcamp.capability.domain.model.TechnologySummary;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del método batch {@link TechnologyGatewayAdapter#findTechnologiesByIds}
 * usando {@link MockWebServer} para simular el Technology_Service.
 *
 * <p>Cubre: mapeo de {@code {id,name,description}} a {@link TechnologySummary}
 * (id + name), emisión del subconjunto devuelto, cortocircuito ante entrada vacía
 * (sin llamada HTTP) y traducción de errores 5xx a
 * {@link TechnologyValidationUnavailableException}.
 *
 * <p>Requirements: 5.2, 5.5, 7.1
 */
class TechnologyGatewayFindByIdsTest {

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
    @DisplayName("Mapea id+name y realiza una única llamada GET con los ids")
    void mapsIdAndNameWithSingleCall() throws InterruptedException {
        String body = "[{\"id\":10,\"name\":\"Java\",\"description\":\"JVM\"},"
                + "{\"id\":11,\"name\":\"Spring\",\"description\":\"Framework\"}]";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body));

        StepVerifier.create(adapter.findTechnologiesByIds(List.of(10L, 11L)))
                .assertNext(t -> {
                    assertThat(t.getId()).isEqualTo(10L);
                    assertThat(t.getName()).isEqualTo("Java");
                })
                .assertNext(t -> {
                    assertThat(t.getId()).isEqualTo(11L);
                    assertThat(t.getName()).isEqualTo("Spring");
                })
                .verifyComplete();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/technologies?ids=10,11");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Solo emite el subconjunto devuelto por el service")
    void emitsOnlyReturnedSubset() {
        String body = "[{\"id\":10,\"name\":\"Java\",\"description\":\"JVM\"}]";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body));

        StepVerifier.create(adapter.findTechnologiesByIds(List.of(10L, 11L, 12L)))
                .assertNext(t -> assertThat(t.getId()).isEqualTo(10L))
                .verifyComplete();
    }

    @Test
    @DisplayName("Entrada vacía retorna Flux.empty sin llamada HTTP")
    void emptyInputReturnsEmptyWithoutHttpCall() {
        StepVerifier.create(adapter.findTechnologiesByIds(List.of()))
                .verifyComplete();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("Un error 5xx se traduce a TechnologyValidationUnavailableException")
    void serverErrorMapsToUnavailableException() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.findTechnologiesByIds(List.of(10L, 11L)))
                .expectError(TechnologyValidationUnavailableException.class)
                .verify();
    }
}
