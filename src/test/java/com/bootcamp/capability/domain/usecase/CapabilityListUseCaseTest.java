package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.exception.InvalidPageQueryException;
import com.bootcamp.capability.domain.exception.PageErrorCode;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.model.PagedResult;
import com.bootcamp.capability.domain.model.TechnologySummary;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del método de listado {@link CapabilityUseCase#listCapabilities}.
 *
 * <p>Mockea ambos puertos SPI y verifica los flujos reactivos con
 * {@link StepVerifier}. Cubre ejemplos y bordes de las reglas del listado
 * (defaults, límites de page/size, página vacía sin gateway, enriquecimiento con
 * ids faltantes, invariante N+1 y propagación del error del gateway), asegurando
 * que ante un rechazo de validación ni {@code findPage}/{@code countAll} ni el
 * gateway se invocan.
 *
 * <p>Requirements: 1.1, 1.2, 1.4, 4.3, 4.4, 4.5, 4.6, 5.1, 5.5, 5.6, 7.1
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapabilityListUseCaseTest {

    @Mock
    private ICapabilityPersistencePort persistencePort;

    @Mock
    private ITechnologyGatewayPort technologyGatewayPort;

    private CapabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapabilityUseCase(persistencePort, technologyGatewayPort);
    }

    private static CapabilityPageQuery query(int page, int size) {
        return new CapabilityPageQuery(page, size,
                CapabilitySortBy.NAME, CapabilitySortDirection.ASC);
    }

    private static Capability capability(long id, String name, List<Long> techIds) {
        return new Capability(id, name, "desc " + id, techIds);
    }

    // ---------------------------------------------------------------------
    // Requirement 4 - validación de rango de page/size
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("page < 0 -> PAGE_NEGATIVE y no consulta persistencia ni gateway")
    void negativePage_rejectedWithoutQuerying() {
        StepVerifier.create(useCase.listCapabilities(query(-1, 10)))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidPageQueryException.class)
                        .extracting(e -> ((InvalidPageQueryException) e).getCode())
                        .isEqualTo(PageErrorCode.PAGE_NEGATIVE))
                .verify();

        verify(persistencePort, never()).findPage(any());
        verify(persistencePort, never()).countAll();
        verify(technologyGatewayPort, never()).findTechnologiesByIds(anyCollection());
    }

    @Test
    @DisplayName("size < 1 -> SIZE_TOO_SMALL y no consulta persistencia ni gateway")
    void tooSmallSize_rejectedWithoutQuerying() {
        StepVerifier.create(useCase.listCapabilities(query(0, 0)))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidPageQueryException.class)
                        .extracting(e -> ((InvalidPageQueryException) e).getCode())
                        .isEqualTo(PageErrorCode.SIZE_TOO_SMALL))
                .verify();

        verify(persistencePort, never()).findPage(any());
        verify(technologyGatewayPort, never()).findTechnologiesByIds(anyCollection());
    }

    @Test
    @DisplayName("size > 100 -> SIZE_TOO_LARGE y no consulta persistencia ni gateway")
    void tooLargeSize_rejectedWithoutQuerying() {
        StepVerifier.create(useCase.listCapabilities(query(0, 101)))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidPageQueryException.class)
                        .extracting(e -> ((InvalidPageQueryException) e).getCode())
                        .isEqualTo(PageErrorCode.SIZE_TOO_LARGE))
                .verify();

        verify(persistencePort, never()).findPage(any());
        verify(technologyGatewayPort, never()).findTechnologiesByIds(anyCollection());
    }

    @Test
    @DisplayName("size = 1 (límite inferior) -> válido")
    void sizeLowerBound_isValid() {
        when(persistencePort.findPage(any())).thenReturn(Flux.empty());
        when(persistencePort.countAll()).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.listCapabilities(query(0, 1)))
                .assertNext(result -> assertThat(result.getSize()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("size = 100 (límite superior) -> válido")
    void sizeUpperBound_isValid() {
        when(persistencePort.findPage(any())).thenReturn(Flux.empty());
        when(persistencePort.countAll()).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.listCapabilities(query(0, 100)))
                .assertNext(result -> assertThat(result.getSize()).isEqualTo(100))
                .verifyComplete();
    }

    // ---------------------------------------------------------------------
    // Requirement 1 / 5.6 - página vacía sin gateway
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Página vacía -> PagedResult vacío con metadata coherente y sin llamar al gateway")
    void emptyPage_returnsEmptyResultWithoutGateway() {
        when(persistencePort.findPage(any())).thenReturn(Flux.empty());
        when(persistencePort.countAll()).thenReturn(Mono.just(23L));

        StepVerifier.create(useCase.listCapabilities(query(5, 10)))
                .assertNext(result -> {
                    assertThat(result.getContent()).isEmpty();
                    assertThat(result.getPage()).isEqualTo(5);
                    assertThat(result.getSize()).isEqualTo(10);
                    assertThat(result.getTotalElements()).isEqualTo(23L);
                    assertThat(result.getTotalPages()).isEqualTo(3);
                })
                .verifyComplete();

        verify(technologyGatewayPort, never()).findTechnologiesByIds(anyCollection());
    }

    // ---------------------------------------------------------------------
    // Requirement 5 - enriquecimiento (N+1, intersección, faltantes)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Enriquecimiento: una sola llamada al gateway con los ids distintos de la página")
    void enrichment_callsGatewayOnceWithDistinctIds() {
        List<Capability> page = List.of(
                capability(1L, "A", List.of(10L, 11L)),
                capability(2L, "B", List.of(11L, 12L)));
        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(page));
        when(persistencePort.countAll()).thenReturn(Mono.just(2L));
        when(technologyGatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(
                        new TechnologySummary(10L, "Java"),
                        new TechnologySummary(11L, "Spring"),
                        new TechnologySummary(12L, "Docker")));

        StepVerifier.create(useCase.listCapabilities(query(0, 10)))
                .expectNextCount(1)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(technologyGatewayPort, times(1)).findTechnologiesByIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(10L, 11L, 12L);
    }

    @Test
    @DisplayName("Enriquecimiento: cada capacidad recibe sus tecnologías (id+name) e ignora las no resueltas")
    void enrichment_mapsResolvedTechnologiesAndOmitsMissing() {
        List<Capability> page = List.of(
                capability(1L, "A", List.of(10L, 11L)),
                capability(2L, "B", List.of(12L, 99L))); // 99 no será resuelto
        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(page));
        when(persistencePort.countAll()).thenReturn(Mono.just(2L));
        when(technologyGatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(
                        new TechnologySummary(10L, "Java"),
                        new TechnologySummary(11L, "Spring"),
                        new TechnologySummary(12L, "Docker")));

        StepVerifier.create(useCase.listCapabilities(query(0, 10)))
                .assertNext(result -> {
                    List<CapabilityListItem> items = result.getContent();
                    assertThat(items).hasSize(2);

                    CapabilityListItem a = items.get(0);
                    assertThat(a.getId()).isEqualTo(1L);
                    assertThat(a.getTechnologies())
                            .extracting(TechnologySummary::getId)
                            .containsExactly(10L, 11L);
                    assertThat(a.getTechnologies())
                            .extracting(TechnologySummary::getName)
                            .containsExactly("Java", "Spring");

                    CapabilityListItem b = items.get(1);
                    assertThat(b.getId()).isEqualTo(2L);
                    // el id 99 se omite por no estar resuelto (Req 5.5)
                    assertThat(b.getTechnologies())
                            .extracting(TechnologySummary::getId)
                            .containsExactly(12L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("El listado preserva el orden emitido por findPage")
    void listing_preservesFindPageOrder() {
        List<Capability> page = List.of(
                capability(3L, "C", List.of(10L)),
                capability(1L, "A", List.of(10L)),
                capability(2L, "B", List.of(10L)));
        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(page));
        when(persistencePort.countAll()).thenReturn(Mono.just(3L));
        when(technologyGatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.just(new TechnologySummary(10L, "Java")));

        StepVerifier.create(useCase.listCapabilities(query(0, 10)))
                .assertNext(result -> assertThat(result.getContent())
                        .extracting(CapabilityListItem::getId)
                        .containsExactly(3L, 1L, 2L))
                .verifyComplete();
    }

    @Test
    @DisplayName("findPage recibe los mismos parámetros del query")
    void findPage_receivesQueryParameters() {
        CapabilityPageQuery q = new CapabilityPageQuery(2, 25,
                CapabilitySortBy.TECHNOLOGY_COUNT, CapabilitySortDirection.DESC);
        when(persistencePort.findPage(any())).thenReturn(Flux.empty());
        when(persistencePort.countAll()).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.listCapabilities(q))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<CapabilityPageQuery> captor =
                ArgumentCaptor.forClass(CapabilityPageQuery.class);
        verify(persistencePort).findPage(captor.capture());
        CapabilityPageQuery used = captor.getValue();
        assertThat(used.getPage()).isEqualTo(2);
        assertThat(used.getSize()).isEqualTo(25);
        assertThat(used.getSortBy()).isEqualTo(CapabilitySortBy.TECHNOLOGY_COUNT);
        assertThat(used.getDirection()).isEqualTo(CapabilitySortDirection.DESC);
    }

    // ---------------------------------------------------------------------
    // Requirement 7.1 - indisponibilidad del gateway propaga el error
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Error del gateway durante el enriquecimiento propaga el error sin producir PagedResult")
    void gatewayError_propagates() {
        List<Capability> page = List.of(capability(1L, "A", List.of(10L)));
        when(persistencePort.findPage(any())).thenReturn(Flux.fromIterable(page));
        when(persistencePort.countAll()).thenReturn(Mono.just(1L));
        when(technologyGatewayPort.findTechnologiesByIds(anyCollection()))
                .thenReturn(Flux.error(
                        new com.bootcamp.capability.domain.exception
                                .TechnologyValidationUnavailableException(
                                new RuntimeException("service down"))));

        StepVerifier.create(useCase.listCapabilities(query(0, 10)))
                .expectError(com.bootcamp.capability.domain.exception
                        .TechnologyValidationUnavailableException.class)
                .verify();
    }
}
