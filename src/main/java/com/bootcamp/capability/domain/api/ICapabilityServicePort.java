package com.bootcamp.capability.domain.api;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.PagedResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface ICapabilityServicePort {

    Mono<Capability> registerCapability(Capability capability);

    Mono<PagedResult<CapabilityListItem>> listCapabilities(CapabilityPageQuery query);

    Flux<CapabilityListItem> findCapabilitiesByIds(Collection<Long> ids);

    Mono<Void> deleteCapabilitiesByIds(Collection<Long> ids);
}
