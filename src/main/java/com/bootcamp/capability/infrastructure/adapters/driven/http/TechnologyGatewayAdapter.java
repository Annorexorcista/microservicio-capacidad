package com.bootcamp.capability.infrastructure.adapters.driven.http;

import com.bootcamp.capability.domain.exception.TechnologyValidationUnavailableException;
import com.bootcamp.capability.domain.model.TechnologySummary;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import com.bootcamp.capability.infrastructure.adapters.driven.http.dto.TechnologyGatewayResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.stream.Collectors;

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

    @Override
    public Flux<TechnologySummary> findTechnologiesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return webClient.get()
                .uri(uri -> uri.path("/api/v1/technologies").queryParam("ids", csv).build())
                .retrieve()
                .bodyToFlux(TechnologyGatewayResponse.class)
                .map(r -> new TechnologySummary(r.id(), r.name()))
                .onErrorMap(ex -> new TechnologyValidationUnavailableException(ex));
    }

    @Override
    public Mono<Void> deleteTechnologiesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.empty();
        }
        String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return webClient.delete()
                .uri(uri -> uri.path("/api/v1/technologies").queryParam("ids", csv).build())
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorMap(ex -> new TechnologyValidationUnavailableException(ex));
    }
}
