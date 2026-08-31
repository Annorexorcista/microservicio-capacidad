package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository;

import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityTechnologyEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface ICapabilityTechnologyRepository
        extends ReactiveCrudRepository<CapabilityTechnologyEntity, Long> {

    Flux<CapabilityTechnologyEntity> findByCapabilityId(Long capabilityId);

    Flux<CapabilityTechnologyEntity> findByTechnologyIdIn(Collection<Long> technologyIds);

    Mono<Void> deleteByCapabilityIdIn(Collection<Long> capabilityIds);
}
