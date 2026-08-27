package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.exception.CapabilityAlreadyExistsException;
import com.bootcamp.capability.domain.exception.DomainErrorCode;
import com.bootcamp.capability.domain.exception.InvalidCapabilityDataException;
import com.bootcamp.capability.domain.exception.TechnologiesNotFoundException;
import com.bootcamp.capability.domain.exception.TechnologyValidationUnavailableException;
import com.bootcamp.capability.domain.model.Capability;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del caso de uso {@link CapabilityUseCase}.
 *
 * <p>Mockea ambos puertos SPI ({@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort}) y verifica los flujos reactivos con
 * {@link StepVerifier}. Cubre ejemplos y bordes de todas las reglas de negocio
 * (Req 1-7) y asegura que ante cualquier rechazo {@code save} nunca se invoca.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapabilityUseCaseTest {

    @Mock
    private ICapabilityPersistencePort persistencePort;

    @Mock
    private ITechnologyGatewayPort technologyGatewayPort;

    private CapabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapabilityUseCase(persistencePort, technologyGatewayPort);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static List<Long> ids(long... values) {
        return LongStream.of(values).boxed().collect(Collectors.toList());
    }

    private static List<Long> idRange(int count) {
        return LongStream.rangeClosed(1, count).boxed().collect(Collectors.toList());
    }

    private static String repeat(char c, int times) {
        return Stream.generate(() -> String.valueOf(c)).limit(times).collect(Collectors.joining());
    }

    /** Stubs para el camino feliz: nombre único y todas las tecnologías existentes. */
    private void stubHappyPath(List<Long> existingIds) {
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(existingIds));
        when(persistencePort.save(any(Capability.class)))
                .thenAnswer(invocation -> {
                    Capability c = invocation.getArgument(0);
                    return Mono.just(new Capability(99L, c.getName(), c.getDescription(),
                            c.getTechnologyIds()));
                });
    }

    // ---------------------------------------------------------------------
    // Requirement 1 / 2.4 / 7.3 - Registro válido
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Registro válido: emite capacidad persistida y llama save exactamente una vez")
    void validRegistration_persistsAndReturnsCapability() {
        List<Long> techIds = idRange(3);
        stubHappyPath(techIds);

        Capability input = new Capability(null, "Backend", "Capacidad de backend", techIds);

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isEqualTo(99L);
                    assertThat(saved.getName()).isEqualTo("Backend");
                    assertThat(saved.getDescription()).isEqualTo("Capacidad de backend");
                    assertThat(saved.getTechnologyIds()).containsExactlyElementsOf(techIds);
                })
                .verifyComplete();

        verify(persistencePort, times(1)).save(any(Capability.class));
    }

    @Test
    @DisplayName("Registro válido: la capacidad persistida es normalizada (trim + ids distintos)")
    void validRegistration_savesNormalizedCapability() {
        List<Long> techIds = idRange(3);
        stubHappyPath(techIds);

        Capability input = new Capability(null, "  Backend  ", "  Desc con espacios  ", techIds);

        StepVerifier.create(useCase.registerCapability(input))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Capability> captor = ArgumentCaptor.forClass(Capability.class);
        verify(persistencePort).save(captor.capture());
        Capability persisted = captor.getValue();
        assertThat(persisted.getId()).isNull();
        assertThat(persisted.getName()).isEqualTo("Backend");
        assertThat(persisted.getDescription()).isEqualTo("Desc con espacios");
        assertThat(persisted.getTechnologyIds()).containsExactlyElementsOf(techIds);
    }

    @Test
    @DisplayName("Trim: nombre y descripción rodeados de espacios son normalizados")
    void trimNormalization_trimsNameAndDescription() {
        List<Long> techIds = idRange(3);
        stubHappyPath(techIds);

        Capability input = new Capability(null, "\t Data \n", "   descripción   ", techIds);

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(saved -> {
                    assertThat(saved.getName()).isEqualTo("Data");
                    assertThat(saved.getDescription()).isEqualTo("descripción");
                })
                .verifyComplete();

        verify(persistencePort).save(any(Capability.class));
    }

    // ---------------------------------------------------------------------
    // Requirement 3 - Nombre
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Nombre nulo -> NAME_REQUIRED y save nunca invocado")
    void nullName_rejectedNameRequired() {
        Capability input = new Capability(null, null, "descripción", idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.NAME_REQUIRED))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Nombre vacío tras trim -> NAME_REQUIRED y save nunca invocado")
    void blankName_rejectedNameRequired() {
        Capability input = new Capability(null, "   ", "descripción", idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.NAME_REQUIRED))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Nombre de 50 caracteres -> válido")
    void nameLength50_isValid() {
        List<Long> techIds = idRange(3);
        stubHappyPath(techIds);
        String name = repeat('a', 50);

        Capability input = new Capability(null, name, "descripción", techIds);

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(saved -> assertThat(saved.getName()).hasSize(50))
                .verifyComplete();

        verify(persistencePort).save(any(Capability.class));
    }

    @Test
    @DisplayName("Nombre de 51 caracteres -> NAME_TOO_LONG y save nunca invocado")
    void nameLength51_rejectedNameTooLong() {
        String name = repeat('a', 51);
        Capability input = new Capability(null, name, "descripción", idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.NAME_TOO_LONG))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Requirement 4 - Descripción
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Descripción nula -> DESCRIPTION_REQUIRED y save nunca invocado")
    void nullDescription_rejectedDescriptionRequired() {
        Capability input = new Capability(null, "Backend", null, idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.DESCRIPTION_REQUIRED))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Descripción vacía tras trim -> DESCRIPTION_REQUIRED y save nunca invocado")
    void blankDescription_rejectedDescriptionRequired() {
        Capability input = new Capability(null, "Backend", "     ", idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.DESCRIPTION_REQUIRED))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Descripción de 90 caracteres -> válida")
    void descriptionLength90_isValid() {
        List<Long> techIds = idRange(3);
        stubHappyPath(techIds);
        String description = repeat('d', 90);

        Capability input = new Capability(null, "Backend", description, techIds);

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(saved -> assertThat(saved.getDescription()).hasSize(90))
                .verifyComplete();

        verify(persistencePort).save(any(Capability.class));
    }

    @Test
    @DisplayName("Descripción de 91 caracteres -> DESCRIPTION_TOO_LONG y save nunca invocado")
    void descriptionLength91_rejectedDescriptionTooLong() {
        String description = repeat('d', 91);
        Capability input = new Capability(null, "Backend", description, idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.DESCRIPTION_TOO_LONG))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Requirement 5 - Cantidad de tecnologías
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("2 tecnologías -> TECHNOLOGIES_TOO_FEW y save nunca invocado")
    void twoTechnologies_rejectedTooFew() {
        Capability input = new Capability(null, "Backend", "descripción", ids(1L, 2L));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.TECHNOLOGIES_TOO_FEW))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("3 tecnologías -> válido")
    void threeTechnologies_isValid() {
        List<Long> techIds = idRange(3);
        stubHappyPath(techIds);

        Capability input = new Capability(null, "Backend", "descripción", techIds);

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(saved -> assertThat(saved.getTechnologyIds()).hasSize(3))
                .verifyComplete();

        verify(persistencePort).save(any(Capability.class));
    }

    @Test
    @DisplayName("20 tecnologías -> válido")
    void twentyTechnologies_isValid() {
        List<Long> techIds = idRange(20);
        stubHappyPath(techIds);

        Capability input = new Capability(null, "Backend", "descripción", techIds);

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(saved -> assertThat(saved.getTechnologyIds()).hasSize(20))
                .verifyComplete();

        verify(persistencePort).save(any(Capability.class));
    }

    @Test
    @DisplayName("21 tecnologías -> TECHNOLOGIES_TOO_MANY y save nunca invocado")
    void twentyOneTechnologies_rejectedTooMany() {
        Capability input = new Capability(null, "Backend", "descripción", idRange(21));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.TECHNOLOGIES_TOO_MANY))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Requirement 6 - Tecnologías repetidas
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Ids de tecnología duplicados -> TECHNOLOGIES_DUPLICATED y save nunca invocado")
    void duplicateTechnologyIds_rejectedDuplicated() {
        Capability input = new Capability(null, "Backend", "descripción", ids(1L, 2L, 2L, 3L));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(InvalidCapabilityDataException.class)
                        .extracting(e -> ((InvalidCapabilityDataException) e).getCode())
                        .isEqualTo(DomainErrorCode.TECHNOLOGIES_DUPLICATED))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Requirement 2 - Unicidad del nombre
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Nombre ya existente -> CapabilityAlreadyExistsException y save nunca invocado")
    void existingName_rejectedAlreadyExists() {
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(true));

        Capability input = new Capability(null, "Backend", "descripción", idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectError(CapabilityAlreadyExistsException.class)
                .verify();

        verify(persistencePort, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Requirement 7 - Existencia de tecnologías
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Alguna tecnología inexistente -> TechnologiesNotFoundException y save nunca invocado")
    void missingTechnology_rejectedNotFound() {
        List<Long> requested = idRange(3);
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        // El gateway solo devuelve un subconjunto (falta el id 3).
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.just(1L, 2L));

        Capability input = new Capability(null, "Backend", "descripción", requested);

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(TechnologiesNotFoundException.class)
                        .extracting(e -> ((TechnologiesNotFoundException) e).getMissingIds())
                        .isEqualTo(List.of(3L)))
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Error del gateway propaga TechnologyValidationUnavailableException y save nunca invocado")
    void gatewayError_propagatesUnavailable() {
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.error(new TechnologyValidationUnavailableException(
                        new RuntimeException("service down"))));

        Capability input = new Capability(null, "Backend", "descripción", idRange(3));

        StepVerifier.create(useCase.registerCapability(input))
                .expectError(TechnologyValidationUnavailableException.class)
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("Todas las tecnologías existen (en desorden) -> registro válido")
    void allTechnologiesExistOutOfOrder_isValid() {
        List<Long> requested = idRange(3);
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.just(3L, 1L, 2L));
        when(persistencePort.save(any(Capability.class)))
                .thenAnswer(invocation -> {
                    Capability c = invocation.getArgument(0);
                    return Mono.just(new Capability(1L, c.getName(), c.getDescription(),
                            c.getTechnologyIds()));
                });

        Capability input = new Capability(null, "Backend", "descripción", requested);

        StepVerifier.create(useCase.registerCapability(input))
                .expectNextCount(1)
                .verifyComplete();

        verify(persistencePort, times(1)).save(any(Capability.class));
    }

    @Test
    @DisplayName("Ids nulos en la lista se descartan; con 3 distintos válidos -> registro exitoso")
    void nullIdsAreDiscarded_isValid() {
        List<Long> withNulls = new ArrayList<>(List.of(1L, 2L, 3L));
        withNulls.add(null);
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.just(1L, 2L, 3L));
        when(persistencePort.save(any(Capability.class)))
                .thenAnswer(invocation -> {
                    Capability c = invocation.getArgument(0);
                    return Mono.just(new Capability(1L, c.getName(), c.getDescription(),
                            c.getTechnologyIds()));
                });

        Capability input = new Capability(null, "Backend", "descripción", withNulls);

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(saved -> assertThat(saved.getTechnologyIds())
                        .containsExactly(1L, 2L, 3L))
                .verifyComplete();

        verify(persistencePort).save(any(Capability.class));
    }
}
