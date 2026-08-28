package com.bootcamp.capability.domain.usecase.list;

import com.bootcamp.capability.domain.exception.TechnologyValidationUnavailableException;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import com.bootcamp.capability.domain.usecase.CapabilityUseCase;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: listar-capacidades, Property 8 — Validates: Requirements 7.1
 *
 * <p><b>Property 8: La indisponibilidad del Technology_Service propaga el error.</b>
 * Para toda página no vacía en la que el gateway emite
 * {@link TechnologyValidationUnavailableException}, {@code listCapabilities}
 * termina con ese mismo error sin producir un {@code PagedResult}.
 */
class CapabilityListProperty8Test {

    @Property(tries = 200)
    void gatewayUnavailablePropagatesError(@ForAll("nonEmptyPages") List<Long> ids) {
        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort gatewayPort = mock(ITechnologyGatewayPort.class);

        List<Capability> page = new ArrayList<>();
        for (Long id : ids) {
            page.add(new Capability(id, "cap" + id, "desc", new ArrayList<>(List.of(1L, 2L))));
        }

        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(page));
        when(persistencePort.countAll()).thenReturn(Mono.just((long) page.size()));
        when(gatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.error(new TechnologyValidationUnavailableException(
                        new RuntimeException("down"))));

        CapabilityUseCase useCase = new CapabilityUseCase(persistencePort, gatewayPort);
        CapabilityPageQuery query = new CapabilityPageQuery(
                0, 100, CapabilitySortBy.NAME, CapabilitySortDirection.ASC);

        Throwable error = null;
        Object result = null;
        try {
            result = useCase.listCapabilities(query).block();
        } catch (Throwable t) {
            error = t;
        }
        if (result != null) {
            throw new AssertionError("no debió producir un PagedResult");
        }
        if (!(error instanceof TechnologyValidationUnavailableException)) {
            throw new AssertionError("esperado TechnologyValidationUnavailableException, obtenido="
                    + error);
        }
    }

    @Provide
    Arbitrary<List<Long>> nonEmptyPages() {
        return Arbitraries.longs().between(1L, 100_000L)
                .list().uniqueElements().ofMinSize(1).ofMaxSize(20);
    }
}
