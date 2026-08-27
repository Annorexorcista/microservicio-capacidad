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
 * Property-based test para la sexta propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 6 — Validates: Requirements 3.2, 4.2
 *
 * <p><b>Property 6: Nombre &gt; 50 o descripción &gt; 90 rechazados sin persistir.</b>
 * Se prueba con dos métodos {@code @Property} independientes, uno por regla:
 * <ul>
 *   <li><b>NAME_TOO_LONG:</b> para todo nombre cuya longitud tras {@code trim}
 *       sea &gt; 50 (51-200, alfanumérico sin espacios de borde de modo que el
 *       {@code trim} no reduzca la longitud por debajo de 51), con una
 *       descripción válida (1-90) y un conjunto de 3-20 ids distintos,
 *       {@code registerCapability} emite un {@link InvalidCapabilityDataException}
 *       cuyo código es {@link DomainErrorCode#NAME_TOO_LONG} y nunca invoca
 *       {@code save}.</li>
 *   <li><b>DESCRIPTION_TOO_LONG:</b> para toda descripción cuya longitud tras
 *       {@code trim} sea &gt; 90 (91-300, alfanumérico sin espacios de borde),
 *       con un nombre válido (1-50) y un conjunto de 3-20 ids distintos,
 *       {@code registerCapability} emite un {@link InvalidCapabilityDataException}
 *       cuyo código es {@link DomainErrorCode#DESCRIPTION_TOO_LONG} y nunca
 *       invoca {@code save}.</li>
 * </ul>
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de
 * cada intento (jqwik no usa la extensión JUnit de Mockito). Los stubs de
 * {@code existsByNameIgnoreCase} y del gateway se configuran de forma laxa
 * ({@code lenient}) porque no deberían alcanzarse: la validación de longitud
 * corta el pipeline antes. En ambos casos se verifica que {@code save} nunca se
 * invoca.
 */
class CapabilityUseCaseProperty6Test {

    @Property(tries = 200)
    void nameLongerThanFiftyIsRejectedWithoutPersisting(
            @ForAll("tooLongNames") String name,
            @ForAll("validDescriptions") String description,
            @ForAll("distinctIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Stubs laxos: no deberían alcanzarse, la regla de longitud de nombre corta antes.
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
                    if (ex.getCode() != DomainErrorCode.NAME_TOO_LONG) {
                        throw new AssertionError(
                                "código de error inesperado: esperado NAME_TOO_LONG obtenido="
                                        + ex.getCode());
                    }
                })
                .verify();

        verify(persistencePort, never()).save(any());
    }

    @Property(tries = 200)
    void descriptionLongerThanNinetyIsRejectedWithoutPersisting(
            @ForAll("validNames") String name,
            @ForAll("tooLongDescriptions") String description,
            @ForAll("distinctIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Stubs laxos: no deberían alcanzarse, la regla de longitud de descripción corta antes.
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
                    if (ex.getCode() != DomainErrorCode.DESCRIPTION_TOO_LONG) {
                        throw new AssertionError(
                                "código de error inesperado: esperado DESCRIPTION_TOO_LONG obtenido="
                                        + ex.getCode());
                    }
                })
                .verify();

        verify(persistencePort, never()).save(any());
    }

    /**
     * Nombres cuya longitud tras {@code trim} es &gt; 50: 51-200 caracteres
     * alfanuméricos (sin espacios de borde, de modo que el {@code trim} no
     * reduzca la longitud por debajo de 51 y siempre se dispare NAME_TOO_LONG).
     */
    @Provide
    Arbitrary<String> tooLongNames() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(51)
                .ofMaxLength(200);
    }

    /**
     * Descripciones cuya longitud tras {@code trim} es &gt; 90: 91-300 caracteres
     * alfanuméricos (sin espacios de borde, de modo que siempre se dispare
     * DESCRIPTION_TOO_LONG).
     */
    @Provide
    Arbitrary<String> tooLongDescriptions() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(91)
                .ofMaxLength(300);
    }

    /**
     * Nombres válidos: 1-50 caracteres alfanuméricos (sin espacios de borde), de
     * modo que la regla de nombre siempre pase y solo pueda dispararse la regla
     * de longitud de descripción.
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
     * dispararse la regla de longitud de nombre.
     */
    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(90);
    }

    /**
     * Conjunto de 3-20 identificadores {@code Long} distintos y positivos, de
     * modo que las validaciones de cantidad y no repetición siempre pasen y solo
     * pueda dispararse la regla de longitud correspondiente.
     */
    @Provide
    Arbitrary<List<Long>> distinctIdSets() {
        return Arbitraries.longs().between(1L, 1_000_000L)
                .list()
                .uniqueElements()
                .ofMinSize(3)
                .ofMaxSize(20);
    }
}
