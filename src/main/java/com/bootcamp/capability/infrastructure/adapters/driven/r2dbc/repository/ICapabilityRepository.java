package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository;

import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ICapabilityRepository extends ReactiveCrudRepository<CapabilityEntity, Long> {

    Mono<Boolean> existsByNameIgnoreCase(String name);
}
