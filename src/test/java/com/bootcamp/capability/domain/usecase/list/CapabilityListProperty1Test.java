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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: listar-capacidades, Property 1 — Validates: Requirements 1.1, 4.6
 *
 * <p><b>Property 1: Listado válido solicita la página correcta y respeta el tamaño.</b>
 * Para todo {@link CapabilityPageQuery} válido (page ≥ 0, size en 1..100) y
 * cualquier contenido devuelto por el puerto de persistencia,
 * {@code listCapabilities} invoca {@code findPage} con el mismo page/size/sortBy/
 * direction del query, y el {@code content} del {@link PagedResult} resultante no
 * contiene más de {@code size} elementos.
 */
class CapabilityListProperty1Test {

    @Property(tries = 200)
    void validQueryUsesSameParametersAndRespectsSize(
            @ForAll @IntRange(min = 0, max = 10_000) int page,
            @ForAll @IntRange(min = 1, max = 100) int size,
            @ForAll("pageContents") List<Capability> content) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort gatewayPort = mock(ITechnologyGatewayPort.class);

        // findPage devuelve como mucho `size` elementos (contrato de la BD).
        List<Capability> limited = content.size() > size ? content.subList(0, size) : content;
        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(limited));
        when(persistencePort.countAll()).thenReturn(Mono.just((long) limited.size()));
        when(gatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(new TechnologySummary(1L, "Tech")));

        CapabilityUseCase useCase = new CapabilityUseCase(persistencePort, gatewayPort);
        CapabilityPageQuery query = new CapabilityPageQuery(
                page, size, CapabilitySortBy.NAME, CapabilitySortDirection.ASC);

        PagedResult<?> result = useCase.listCapabilities(query).block();

        if (result == null) {
            throw new AssertionError("El resultado no debe ser nulo para un query válido");
        }
        if (result.getContent().size() > size) {
            throw new AssertionError("El content excede el size: " + result.getContent().size()
                    + " > " + size);
        }

        org.mockito.ArgumentCaptor<CapabilityPageQuery> captor =
                org.mockito.ArgumentCaptor.forClass(CapabilityPageQuery.class);
        org.mockito.Mockito.verify(persistencePort).findPage(captor.capture());
        CapabilityPageQuery used = captor.getValue();
        if (used.getPage() != page || used.getSize() != size
                || used.getSortBy() != CapabilitySortBy.NAME
                || used.getDirection() != CapabilitySortDirection.ASC) {
            throw new AssertionError("findPage no recibió los parámetros del query");
        }
    }

    @Provide
    Arbitrary<List<Capability>> pageContents() {
        Arbitrary<Capability> capability = Arbitraries.longs().between(1L, 100_000L)
                .map(id -> new Capability(id, "cap" + id, "desc",
                        new ArrayList<>(List.of(1L, 2L, 3L))));
        return capability.list().ofMinSize(0).ofMaxSize(100);
    }
}
