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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: listar-capacidades, Property 3 — Validates: Requirements 2.1, 2.2, 3.1, 3.2
 *
 * <p><b>Property 3: El listado preserva el orden devuelto por la base de datos.</b>
 * Para toda secuencia de capacidades emitida por {@code findPage}, el
 * {@code content} del {@link PagedResult} (tras el enriquecimiento) preserva
 * exactamente ese mismo orden posicional, sin reordenar en memoria.
 */
class CapabilityListProperty3Test {

    @Property(tries = 200)
    void listingPreservesDatabaseOrder(@ForAll("idSequences") List<Long> ids) {
        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort gatewayPort = mock(ITechnologyGatewayPort.class);

        List<Capability> page = new ArrayList<>();
        for (Long id : ids) {
            page.add(new Capability(id, "cap" + id, "desc", new ArrayList<>(List.of(1L))));
        }

        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(page));
        when(persistencePort.countAll()).thenReturn(Mono.just((long) page.size()));
        when(gatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(new TechnologySummary(1L, "Tech")));

        CapabilityUseCase useCase = new CapabilityUseCase(persistencePort, gatewayPort);
        CapabilityPageQuery query = new CapabilityPageQuery(
                0, 100, CapabilitySortBy.NAME, CapabilitySortDirection.ASC);

        PagedResult<CapabilityListItem> result = useCase.listCapabilities(query).block();
        if (result == null) {
            throw new AssertionError("resultado nulo");
        }
        List<Long> resultIds = result.getContent().stream()
                .map(CapabilityListItem::getId).toList();
        if (!resultIds.equals(ids)) {
            throw new AssertionError("orden alterado: esperado=" + ids + " obtenido=" + resultIds);
        }
    }

    @Provide
    Arbitrary<List<Long>> idSequences() {
        return Arbitraries.longs().between(1L, 100_000L)
                .list().uniqueElements().ofMinSize(0).ofMaxSize(50);
    }
}
