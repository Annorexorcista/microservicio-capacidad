package com.bootcamp.capability.infrastructure.adapters.driven.http;

import com.bootcamp.capability.domain.exception.TechnologyValidationUnavailableException;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import com.bootcamp.capability.infrastructure.adapters.driven.http.dto.TechnologyGatewayResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Adaptador driven que implementa {@link ITechnologyGatewayPort} consultando al
 * microservicio de Tecnología de forma no bloqueante mediante {@link WebClient}.
 *
 * <p>Consume {@code GET /api/v1/technologies?ids=1,2,3}, que devuelve
 * {@code [{id, name, description}]} únicamente de las tecnologías existentes.
 *
 * <p>Es una clase plana (sin {@code @Component}); el cableado del bean se realiza
 * en {@code BeanConfiguration}, y el {@link WebClient} con su {@code baseUrl} se
 * configura en {@code WebClientConfig}.
 */
public class TechnologyGatewayAdapter implements ITechnologyGatewayPort {

    private final WebClient webClient;

    public TechnologyGatewayAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Flux<Long> findExistingTechnologyIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return webClient.get()
                .uri(uri -> uri.path("/api/v1/technologies").queryParam("ids", csv).build())
                .retrieve()
                .bodyToFlux(TechnologyGatewayResponse.class)
                .map(TechnologyGatewayResponse::id)
                .onErrorMap(ex -> new TechnologyValidationUnavailableException(ex));
    }
}
