package com.bootcamp.capability.domain.usecase.list;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.model.PagedResult;
import com.bootcamp.capability.domain.model.TechnologySummary;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import com.bootcamp.capability.domain.usecase.CapabilityUseCase;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: listar-capacidades, Property 2 — Validates: Requirements 1.2, 1.4
 *
 * <p><b>Property 2: Invariante de metadata de paginación.</b>
 * Para todo {@code totalElements} ≥ 0, {@code size} en 1..100 y {@code page} ≥ 0,
 * el {@link PagedResult} cumple que {@code totalPages} es el techo de
 * {@code totalElements/size}; y cuando el offset ({@code page*size}) es ≥
 * {@code totalElements} el {@code content} es vacío mientras {@code totalElements}
 * y {@code totalPages} conservan sus valores.
 */
class CapabilityListProperty2Test {

    @Property(tries = 200)
    void paginationMetadataInvariant(
            @ForAll @LongRange(min = 0, max = 100_000) long totalElements,
            @ForAll @IntRange(min = 1, max = 100) int size,
            @ForAll @IntRange(min = 0, max = 10_000) int page) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort gatewayPort = mock(ITechnologyGatewayPort.class);

        long offset = (long) page * size;
        // Simula la BD: si el offset queda fuera del total, la página es vacía.
        boolean outOfRange = offset >= totalElements;
        List<Capability> pageContent = new ArrayList<>();
        if (!outOfRange) {
            long remaining = totalElements - offset;
            int count = (int) Math.min(size, remaining);
            for (int i = 0; i < count; i++) {
                pageContent.add(new Capability((long) (offset + i), "c", "d",
                        new ArrayList<>(List.of(1L))));
            }
        }

        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(pageContent));
        when(persistencePort.countAll()).thenReturn(Mono.just(totalElements));
        when(gatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(new TechnologySummary(1L, "Tech")));

        CapabilityUseCase useCase = new CapabilityUseCase(persistencePort, gatewayPort);
        CapabilityPageQuery query = new CapabilityPageQuery(
                page, size, CapabilitySortBy.NAME, CapabilitySortDirection.ASC);

        PagedResult<?> result = useCase.listCapabilities(query).block();
        if (result == null) {
            throw new AssertionError("resultado nulo");
        }

        int expectedTotalPages = (int) Math.ceil((double) totalElements / size);
        if (result.getTotalPages() != expectedTotalPages) {
            throw new AssertionError("totalPages esperado=" + expectedTotalPages
                    + " obtenido=" + result.getTotalPages());
        }
        if (result.getTotalElements() != totalElements) {
            throw new AssertionError("totalElements alterado");
        }
        if (outOfRange && !result.getContent().isEmpty()) {
            throw new AssertionError("página fuera de rango debe tener content vacío");
        }
    }
}
