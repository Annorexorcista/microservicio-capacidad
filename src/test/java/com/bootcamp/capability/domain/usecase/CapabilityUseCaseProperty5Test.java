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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based test para la quinta propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 5 — Validates: Requirements 4.1
 *
 * <p><b>Property 5: Descripción vacía/obligatoria rechazada sin persistir.</b>
 * Para toda descripción que tras {@code trim} quede vacía (cadena vacía o
 * compuesta únicamente por espacios en blanco: espacios, tabuladores y saltos
 * de línea), con un nombre válido (1-50 tras trim, alfanumérico) y un conjunto
 * de 3-20 ids distintos (de modo que SOLO se dispare la regla de descripción
 * obligatoria), {@code registerCapability} emite un
 * {@link InvalidCapabilityDataException} cuyo código es
 * {@link DomainErrorCode#DESCRIPTION_REQUIRED} y nunca invoca {@code save}.
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de
 * cada intento (jqwik no usa la extensión JUnit de Mockito). Los stubs de
 * {@code existsByNameIgnoreCase} y del gateway se configuran de forma laxa (no
 * deberían alcanzarse, porque la validación de descripción corta el pipeline
 * antes), y {@code save} se verifica que nunca se invoque.
 */
class CapabilityUseCaseProperty5Test {

    @Property(tries = 200)
    void emptyOrBlankDescriptionIsRejectedWithoutPersisting(
            @ForAll("validNames") String name,
            @ForAll("blankDescriptions") String rawDescription,
            @ForAll("distinctIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Stubs laxos: no deberían alcanzarse, la regla de descripción corta antes.
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(ids));

        CapabilityUseCase useCase =
                new CapabilityUseCase(persistencePort, technologyGatewayPort);

        Capability input = new Capability(null, name, rawDescription, new ArrayList<>(ids));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(error -> {
                    if (!(error instanceof InvalidCapabilityDataException ex)) {
                        throw new AssertionError(
                                "tipo de error inesperado: esperado InvalidCapabilityDataException"
                                        + " obtenido=" + error.getClass().getName());
                    }
                    if (ex.getCode() != DomainErrorCode.DESCRIPTION_REQUIRED) {
                        throw new AssertionError(
                                "código de error inesperado: esperado DESCRIPTION_REQUIRED obtenido="
                                        + ex.getCode());
                    }
                })
                .verify();

        verify(persistencePort, never()).save(any());
    }

    /**
     * Nombres válidos: 1-50 caracteres alfanuméricos (sin espacios de borde), de
     * modo que la regla de nombre siempre pase y solo pueda dispararse la regla
     * de descripción obligatoria.
     */
    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    /**
     * Descripciones que tras {@code trim} quedan vacías: cadenas vacías o
     * compuestas únicamente por espacios en blanco (espacios, tabuladores, saltos
     * de línea y retornos de carro), de longitud 0-6.
     */
    @Provide
    Arbitrary<String> blankDescriptions() {
        return Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r')
                .ofMinLength(0)
                .ofMaxLength(6);
    }

    /**
     * Conjunto de 3-20 identificadores {@code Long} distintos y positivos, de
     * modo que las validaciones de cantidad y no repetición siempre pasen y solo
     * pueda dispararse la regla de descripción obligatoria.
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
