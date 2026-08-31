package com.bootcamp.capability.infrastructure.adapters.driving.webflux.handler;

import com.bootcamp.capability.domain.api.ICapabilityServicePort;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityRequest;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.mapper.CapabilityDtoMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public class CapabilityHandler {

    private final ICapabilityServicePort servicePort;
    private final CapabilityDtoMapper dtoMapper;

    public CapabilityHandler(ICapabilityServicePort servicePort, CapabilityDtoMapper dtoMapper) {
        this.servicePort = servicePort;
        this.dtoMapper = dtoMapper;
    }

    public Mono<ServerResponse> register(ServerRequest request) {
        return request.bodyToMono(CapabilityRequest.class)
                .map(dtoMapper::toDomain)
                .flatMap(servicePort::registerCapability)
                .map(dtoMapper::toResponse)
                .flatMap(response -> ServerResponse
                        .status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        if (request.queryParam("ids").isPresent()) {
            return findByIds(request);
        }
        return Mono.fromCallable(() -> dtoMapper.toPageQuery(request))
                .flatMap(servicePort::listCapabilities)
                .map(dtoMapper::toPageResponse)
                .flatMap(response -> ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    private Mono<ServerResponse> findByIds(ServerRequest request) {
        return Mono.fromCallable(() -> dtoMapper.parseIds(request))
                .flatMapMany(servicePort::findCapabilitiesByIds)
                .map(dtoMapper::toListItemResponse)
                .collectList()
                .flatMap(list -> ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    public Mono<ServerResponse> deleteByIds(ServerRequest request) {
        return Mono.fromCallable(() -> dtoMapper.parseIds(request))
                .flatMap(servicePort::deleteCapabilitiesByIds)
                .then(ServerResponse.noContent().build());
    }
}
