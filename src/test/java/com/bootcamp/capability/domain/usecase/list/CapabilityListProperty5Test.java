package com.bootcamp.capability.domain.usecase.list;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.model.TechnologySummary;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import com.bootcamp.capability.domain.usecase.CapabilityUseCase;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: listar-capacidades, Property 5 — Validates: Requirements 5.1, 5.2, 5.6
 *
 * <p><b>Property 5: Invariante N+1 en el enriquecimiento.</b>
 * Para toda página de capacidades, {@code listCapabilities} invoca el gateway a lo
 * sumo una vez: cero veces si la página es vacía, y exactamente una vez con el
 * conjunto de identificadores de tecnología distintos (unión sin duplicados)
 * presentes en la página en caso contrario.
 */
class CapabilityListProperty5Test {

    @Property(tries = 200)
    void gatewayCalledAtMostOnceWithDistinctIds(@ForAll("pages") List<List<Long>> capabilityTechIds) {
        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort gatewayPort = mock(ITechnologyGatewayPort.class);

        List<Capability> page = new ArrayList<>();
        long id = 1;
        for (List<Long> techIds : capabilityTechIds) {
            page.add(new Capability(id++, "cap", "desc", new ArrayList<>(techIds)));
        }

        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(page));
        when(persistencePort.countAll()).thenReturn(Mono.just((long) page.size()));
        when(gatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.empty());

        CapabilityUseCase useCase = new CapabilityUseCase(persistencePort, gatewayPort);
        CapabilityPageQuery query = new CapabilityPageQuery(
                0, 100, CapabilitySortBy.NAME, CapabilitySortDirection.ASC);

        useCase.listCapabilities(query).block();

        boolean emptyPage = page.isEmpty();
        if (emptyPage) {
            verify(gatewayPort, never()).findTechnologiesByIds(anyCollection());
            return;
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(gatewayPort, times(1)).findTechnologiesByIds(captor.capture());

        Set<Long> expectedDistinct = new LinkedHashSet<>();
        capabilityTechIds.forEach(expectedDistinct::addAll);

        Collection<Long> actual = captor.getValue();
        Set<Long> actualSet = new LinkedHashSet<>(actual);
        if (actualSet.size() != actual.size()) {
            throw new AssertionError("el gateway recibió ids duplicados: " + actual);
        }
        if (!actualSet.equals(expectedDistinct)) {
            throw new AssertionError("conjunto de ids esperado=" + expectedDistinct
                    + " obtenido=" + actualSet);
        }
    }

    @Provide
    Arbitrary<List<List<Long>>> pages() {
        Arbitrary<List<Long>> techIdLists = Arbitraries.longs().between(1L, 50L)
                .list().uniqueElements().ofMinSize(1).ofMaxSize(5);
        return techIdLists.list().ofMinSize(0).ofMaxSize(10);
    }
}
