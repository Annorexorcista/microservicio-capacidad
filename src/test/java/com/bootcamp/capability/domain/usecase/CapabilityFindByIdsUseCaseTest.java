package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.TechnologySummary;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link CapabilityUseCase#findCapabilitiesByIds}, la consulta
 * por identificadores usada por otros microservicios (por ejemplo, Bootcamp).
 *
 * <p>Verifica que cada capacidad recuperada se enriquece con sus tecnologías
 * (id + name) reutilizando el enriquecimiento por lotes, que se preserva el orden
 * devuelto por persistencia, que las tecnologías no resueltas se omiten y que una
 * entrada vacía no consulta persistencia ni gateway.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapabilityFindByIdsUseCaseTest {

    @Mock
    private ICapabilityPersistencePort persistencePort;

    @Mock
    private ITechnologyGatewayPort technologyGatewayPort;

    private CapabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapabilityUseCase(persistencePort, technologyGatewayPort);
    }

    private static Capability capability(long id, String name, List<Long> techIds) {
        return new Capability(id, name, "desc " + id, techIds);
    }

    @Test
    @DisplayName("Recupera capacidades por id y las enriquece con sus tecnologías (id+name)")
    void findByIds_enrichesWithTechnologies() {
        List<Capability> found = List.of(
                capability(1L, "Backend", List.of(10L, 11L)),
                capability(2L, "DevOps", List.of(12L)));
        when(persistencePort.findByIds(anyCollection())).thenReturn(Flux.fromIterable(found));
        when(technologyGatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(
                        new TechnologySummary(10L, "Java"),
                        new TechnologySummary(11L, "Spring"),
                        new TechnologySummary(12L, "Docker")));

        StepVerifier.create(useCase.findCapabilitiesByIds(List.of(1L, 2L)).collectList())
                .assertNext(items -> {
                    assertThat(items).extracting(CapabilityListItem::getId).containsExactly(1L, 2L);
                    assertThat(items.get(0).getTechnologies())
                            .extracting(TechnologySummary::getName)
                            .containsExactly("Java", "Spring");
                    assertThat(items.get(1).getTechnologies())
                            .extracting(TechnologySummary::getId)
                            .containsExactly(12L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Omite las tecnologías no resueltas por el Technology_Service")
    void findByIds_omitsUnresolvedTechnologies() {
        when(persistencePort.findByIds(anyCollection()))
                .thenReturn(Flux.just(capability(1L, "Backend", List.of(10L, 99L))));
        when(technologyGatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(new TechnologySummary(10L, "Java"))); // 99 no resuelto

        StepVerifier.create(useCase.findCapabilitiesByIds(List.of(1L)))
                .assertNext(item -> assertThat(item.getTechnologies())
                        .extracting(TechnologySummary::getId)
                        .containsExactly(10L))
                .verifyComplete();
    }

    @Test
    @DisplayName("Ninguna capacidad encontrada -> vacío sin llamar al gateway")
    void findByIds_emptyWhenNoneFound() {
        when(persistencePort.findByIds(anyCollection())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.findCapabilitiesByIds(List.of(1L, 2L)))
                .verifyComplete();

        verify(technologyGatewayPort, never()).findTechnologiesByIds(anyCollection());
    }

    @Test
    @DisplayName("Entrada vacía -> vacío (persistencia devuelve vacío, sin gateway)")
    void findByIds_emptyInput() {
        when(persistencePort.findByIds(anyCollection())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.findCapabilitiesByIds(List.of()))
                .verifyComplete();

        verify(technologyGatewayPort, never()).findTechnologiesByIds(any());
    }
}
