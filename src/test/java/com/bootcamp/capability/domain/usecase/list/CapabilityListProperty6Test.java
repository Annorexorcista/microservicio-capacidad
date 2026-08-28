package com.bootcamp.capability.domain.usecase.list;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.model.PagedResult;
import com.bootcamp.capability.domain.model.TechnologySummary;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: listar-capacidades, Property 6 — Validates: Requirements 5.3, 5.5
 *
 * <p><b>Property 6: Correctitud del enriquecimiento de tecnologías.</b>
 * Para toda página de capacidades y todo subconjunto de tecnologías resueltas por
 * el gateway, las tecnologías asociadas a cada {@link CapabilityListItem} son
 * exactamente la intersección de los {@code technologyId} de esa capacidad con los
 * identificadores resueltos, cada una con su id y name, y sin incluir
 * identificadores no resueltos.
 */
class CapabilityListProperty6Test {

    @Property(tries = 200)
    void enrichmentIsIntersectionWithResolved(
            @ForAll("techIdLists") List<Long> techIds,
            @ForAll("resolvedFraction") int resolvedCount) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort gatewayPort = mock(ITechnologyGatewayPort.class);

        // La capacidad tiene todos los techIds; el gateway resuelve solo los primeros N.
        int n = Math.min(resolvedCount, techIds.size());
        List<Long> resolvedIds = techIds.subList(0, n);
        List<TechnologySummary> resolved = resolvedIds.stream()
                .map(id -> new TechnologySummary(id, "name" + id)).toList();

        Capability cap = new Capability(1L, "cap", "desc", new ArrayList<>(techIds));
        when(persistencePort.findPage(any())).thenReturn(Flux.just(cap));
        when(persistencePort.countAll()).thenReturn(Mono.just(1L));
        when(gatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.fromIterable(resolved));

        CapabilityUseCase useCase = new CapabilityUseCase(persistencePort, gatewayPort);
        CapabilityPageQuery query = new CapabilityPageQuery(
                0, 100, CapabilitySortBy.NAME, CapabilitySortDirection.ASC);

        PagedResult<CapabilityListItem> result = useCase.listCapabilities(query).block();
        if (result == null || result.getContent().size() != 1) {
            throw new AssertionError("resultado inesperado");
        }
        CapabilityListItem item = result.getContent().get(0);

        // Intersección esperada: ids de la capacidad que están resueltos, en orden de la capacidad.
        Set<Long> resolvedSet = new LinkedHashSet<>(resolvedIds);
        List<Long> expected = techIds.stream().filter(resolvedSet::contains).toList();
        List<Long> actual = item.getTechnologies().stream().map(TechnologySummary::getId).toList();

        if (!actual.equals(expected)) {
            throw new AssertionError("intersección esperada=" + expected + " obtenida=" + actual);
        }
        // Cada tecnología resuelta lleva su name correcto.
        for (TechnologySummary t : item.getTechnologies()) {
            if (!("name" + t.getId()).equals(t.getName())) {
                throw new AssertionError("name incorrecto para id=" + t.getId());
            }
        }
    }

    @Provide
    Arbitrary<List<Long>> techIdLists() {
        return Arbitraries.longs().between(1L, 100L)
                .list().uniqueElements().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<Integer> resolvedFraction() {
        return Arbitraries.integers().between(0, 10);
    }
}
