package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.exception.CapabilityAlreadyExistsException;
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
 * Property-based test para la segunda propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 2 — Validates: Requirements 2.1,
 * 2.2, 2.3
 *
 * <p><b>Property 2: Nombre duplicado se rechaza sin persistir (case-insensitive).</b>
 * Para todo nombre válido (1-50 tras trim) tal que el puerto de persistencia
 * reporta su existencia ({@code existsByNameIgnoreCase -> true}), con
 * descripción válida (1-90 tras trim) y un conjunto de 3-20 ids distintos (de
 * modo que la única regla que rechaza es la unicidad del nombre),
 * {@code registerCapability} emite {@link CapabilityAlreadyExistsException} y
 * nunca invoca {@code save}. La comparación es sin distinción entre mayúsculas
 * y minúsculas.
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de
 * cada intento (jqwik no usa la extensión JUnit de Mockito). El stub de
 * {@code existsByNameIgnoreCase} devuelve siempre {@code true} para forzar el
 * conflicto de unicidad; el gateway devuelve todos los ids solicitados como
 * existentes, aunque el pipeline debe cortocircuitar antes de consultarlo.
 */
class CapabilityUseCaseProperty2Test {

    @Property(tries = 200)
    void duplicateNameIsRejectedWithoutPersisting(
            @ForAll("validNames") String rawName,
            @ForAll("validDescriptions") String rawDescription,
            @ForAll("distinctIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // El nombre ya existe (comparación case-insensitive en el adaptador real).
        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(true));
        // El gateway reporta todos los ids como existentes; el pipeline no debería
        // llegar a consultarlo porque la unicidad falla antes.
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(ids));

        CapabilityUseCase useCase =
                new CapabilityUseCase(persistencePort, technologyGatewayPort);

        Capability input = new Capability(null, rawName, rawDescription, new ArrayList<>(ids));

        StepVerifier.create(useCase.registerCapability(input))
                .expectError(CapabilityAlreadyExistsException.class)
                .verify();

        verify(persistencePort, never()).save(any(Capability.class));
    }

    /**
     * Nombres cuyo valor tras {@code trim} tiene longitud 1-50, opcionalmente
     * rodeados de espacios en blanco de borde (que no alteran la longitud útil),
     * de modo que el nombre sea siempre válido y solo la regla de unicidad lo
     * rechace.
     */
    @Provide
    Arbitrary<String> validNames() {
        return trimmedTextWithPadding(1, 50);
    }

    /**
     * Descripciones cuyo valor tras {@code trim} tiene longitud 1-90,
     * opcionalmente rodeadas de espacios en blanco de borde.
     */
    @Provide
    Arbitrary<String> validDescriptions() {
        return trimmedTextWithPadding(1, 90);
    }

    /**
     * Genera un texto de longitud {@code min}-{@code max} compuesto por letras y
     * dígitos (sin espacios internos), y le añade padding de espacios en los
     * bordes, de modo que la longitud tras {@code trim} permanezca en el rango.
     */
    private Arbitrary<String> trimmedTextWithPadding(int min, int max) {
        Arbitrary<String> core = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(min)
                .ofMaxLength(max);
        Arbitrary<String> leftPad = Arbitraries.strings().withChars(' ').ofMaxLength(3);
        Arbitrary<String> rightPad = Arbitraries.strings().withChars(' ').ofMaxLength(3);
        return net.jqwik.api.Combinators.combine(leftPad, core, rightPad)
                .as((l, c, r) -> l + c + r);
    }

    /**
     * Conjunto de 3-20 identificadores {@code Long} distintos y positivos, de
     * modo que las validaciones de cantidad y no repetición siempre pasen.
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
