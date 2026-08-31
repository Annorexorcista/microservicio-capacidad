package com.bootcamp.capability.domain.spi;

import com.bootcamp.capability.domain.model.TechnologySummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface ITechnologyGatewayPort {

    Flux<Long> findExistingTechnologyIds(Collection<Long> ids);

    Flux<TechnologySummary> findTechnologiesByIds(Collection<Long> ids);

    Mono<Void> deleteTechnologiesByIds(Collection<Long> ids);
}
