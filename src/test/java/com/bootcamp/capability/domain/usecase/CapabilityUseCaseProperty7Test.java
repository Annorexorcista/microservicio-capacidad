package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.exception.DomainErrorCode;
import com.bootcamp.capability.domain.exception.InvalidCapabilityDataException;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Property-based test para la séptima propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 7 — Validates: Requirements 5.1, 5.2
 *
 * <p><b>Property 7: Cantidad de tecnologías fuera de rango rechazada sin
 * persistir.</b> Se prueba con dos métodos {@code @Property} independientes, uno
 * por regla:
 * <ul>
 *   <li><b>TECHNOLOGIES_TOO_FEW:</b> para todo conjunto de ids distintos cuyo
 *       tamaño sea &lt; 3 (es decir, 0, 1 o 2 elementos), con un nombre válido
 *       (1-50) y una descripción válida (1-90),
 *       {@code registerCapability} emite un {@link InvalidCapabilityDataException}
 *       cuyo código es {@link DomainErrorCode#TECHNOLOGIES_TOO_FEW} y nunca invoca
 *       {@code save}.</li>
 *   <li><b>TECHNOLOGIES_TOO_MANY:</b> para todo conjunto de ids distintos cuyo
 *       tamaño sea &gt; 20 (21-40 elementos), con un nombre válido (1-50) y una
 *       descripción válida (1-90),
 *       {@code registerCapability} emite un {@link InvalidCapabilityDataException}
 *       cuyo código es {@link DomainErrorCode#TECHNOLOGIES_TOO_MANY} y nunca
 *       invoca {@code save}.</li>
 * </ul>
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de
 * cada intento (jqwik no usa la extensión JUnit de Mockito). Los stubs de
 * {@code existsByNameIgnoreCase} y del gateway se configuran de forma laxa
 * ({@code lenient}) porque no deberían alcanzarse: la validación de cantidad
 * corta el pipeline antes. En ambos casos se verifica que {@code save} nunca se
 * invoca.
 */
class CapabilityUseCaseProperty7Test {

    @Property(tries = 200)
    void tooFewTechnologiesIsRejectedWithoutPersisting(
            @ForAll("validNames") String name,
            @ForAll("validDescriptions") String description,
            @ForAll("tooFewIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Stubs laxos: no deberían alcanzarse, la regla de cantidad mínima corta antes.
        lenient().when(persistencePort.existsByNameIgnoreCase(anyString()))
                .thenReturn(Mono.just(false));
        lenient().when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(ids));

        CapabilityUseCase useCase =
                new CapabilityUseCase(persistencePort, technologyGatewayPort);

        Capability input = new Capability(null, name, description, new ArrayList<>(ids));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(error -> {
                    if (!(error instanceof InvalidCapabilityDataException ex)) {
                        throw new AssertionError(
                                "tipo de error inesperado: esperado InvalidCapabilityDataException"
                                        + " obtenido=" + error.getClass().getName());
                    }
                    if (ex.getCode() != DomainErrorCode.TECHNOLOGIES_TOO_FEW) {
                        throw new AssertionError(
                                "código de error inesperado: esperado TECHNOLOGIES_TOO_FEW obtenido="
                                        + ex.getCode());
                    }
                })
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Property(tries = 200)
    void tooManyTechnologiesIsRejectedWithoutPersisting(
            @ForAll("validNames") String name,
            @ForAll("validDescriptions") String description,
            @ForAll("tooManyIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Stubs laxos: no deberían alcanzarse, la regla de cantidad máxima corta antes.
        lenient().when(persistencePort.existsByNameIgnoreCase(anyString()))
                .thenReturn(Mono.just(false));
        lenient().when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(ids));

        CapabilityUseCase useCase =
                new CapabilityUseCase(persistencePort, technologyGatewayPort);

        Capability input = new Capability(null, name, description, new ArrayList<>(ids));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(error -> {
                    if (!(error instanceof InvalidCapabilityDataException ex)) {
                        throw new AssertionError(
                                "tipo de error inesperado: esperado InvalidCapabilityDataException"
                                        + " obtenido=" + error.getClass().getName());
                    }
                    if (ex.getCode() != DomainErrorCode.TECHNOLOGIES_TOO_MANY) {
                        throw new AssertionError(
                                "código de error inesperado: esperado TECHNOLOGIES_TOO_MANY obtenido="
                                        + ex.getCode());
                    }
                })
                .verify();

        verify(persistencePort, never()).save(any());
    }

    /**
     * Nombres válidos: 1-50 caracteres alfanuméricos (sin espacios de borde), de
     * modo que la regla de nombre siempre pase y solo pueda dispararse la regla
     * de cantidad de tecnologías.
     */
    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    /**
     * Descripciones válidas: 1-90 caracteres alfanuméricos (sin espacios de
     * borde), de modo que la regla de descripción siempre pase y solo pueda
     * dispararse la regla de cantidad de tecnologías.
     */
    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(90);
    }

    /**
     * Conjuntos de identificadores {@code Long} distintos y positivos con tamaño
     * &lt; 3: es decir, 0, 1 o 2 elementos. Con nombre y descripción válidos y sin
     * duplicados, el único fallo posible es {@code TECHNOLOGIES_TOO_FEW} (Req 5.1).
     */
    @Provide
    Arbitrary<List<Long>> tooFewIdSets() {
        return Arbitraries.longs().between(1L, 1_000_000L)
                .list()
                .uniqueElements()
                .ofMinSize(0)
                .ofMaxSize(2);
    }

    /**
     * Conjuntos de identificadores {@code Long} distintos y positivos con tamaño
     * &gt; 20: es decir, 21-40 elementos. Con nombre y descripción válidos y sin
     * duplicados, el único fallo posible es {@code TECHNOLOGIES_TOO_MANY} (Req 5.2).
     */
    @Provide
    Arbitrary<List<Long>> tooManyIdSets() {
        return Arbitraries.longs().between(1L, 1_000_000L)
                .list()
                .uniqueElements()
                .ofMinSize(21)
                .ofMaxSize(40);
    }
}
