package com.bootcamp.capability.domain.spi;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface ICapabilityPersistencePort {

    Mono<Boolean> existsByNameIgnoreCase(String normalizedName);

    Mono<Capability> save(Capability capability);

    Flux<Capability> findPage(CapabilityPageQuery query);

    Mono<Long> countAll();

    Flux<Capability> findByIds(Collection<Long> ids);

    Flux<Long> deleteByIdsReturningOrphanTechnologyIds(Collection<Long> ids);
}
