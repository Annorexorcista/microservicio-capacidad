package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.exception.TechnologiesNotFoundException;
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
 * Property-based test para la novena propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 9 — Validates: Requirements 7.1, 7.2
 *
 * <p><b>Property 9: Tecnología inexistente rechaza el registro sin persistir.</b>
 * Para todo conjunto válido de identificadores (entre 3 y 20 distintos), con un
 * nombre válido (1-50) y una descripción válida (1-90) —de modo que la única
 * regla que puede fallar sea la de existencia—, si el gateway reporta que al
 * menos uno de los identificadores solicitados no existe (devuelve un
 * subconjunto estricto de los solicitados), {@code registerCapability} emite un
 * {@link TechnologiesNotFoundException} cuyos {@code missingIds} contienen
 * exactamente los identificadores omitidos por el gateway, y nunca invoca
 * {@code save} (Req 7.1, 7.2).
 *
 * <p>El generador {@code missingSubsetCases} produce, para cada intento, la lista
 * de identificadores solicitados junto con el número {@code k} de identificadores
 * a omitir (1..tamaño-1). El stub de {@code findExistingTechnologyIds} devuelve
 * los identificadores solicitados menos los primeros {@code k} (el subconjunto
 * "existente"), garantizando que siempre falte al menos uno.
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de cada
 * intento (jqwik no usa la extensión JUnit de Mockito). El stub de
 * {@code existsByNameIgnoreCase} se configura de forma laxa
 * ({@code lenient}) porque el nombre siempre es válido y único. En todos los casos
 * se verifica que {@code save} nunca se invoca.
 */
class CapabilityUseCaseProperty9Test {

    @Property(tries = 200)
    void nonExistentTechnologyIsRejectedWithoutPersisting(
            @ForAll("validNames") String name,
            @ForAll("validDescriptions") String description,
            @ForAll("missingSubsetCases") SubsetCase subsetCase) {

        List<Long> requested = subsetCase.requestedIds();
        // Subconjunto estricto: los ids solicitados menos los primeros k (k >= 1).
        List<Long> existing = requested.subList(subsetCase.dropCount(), requested.size());
        List<Long> missing = new ArrayList<>(requested.subList(0, subsetCase.dropCount()));

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Nombre válido y único: el pipeline llega hasta la validación de existencia.
        lenient().when(persistencePort.existsByNameIgnoreCase(anyString()))
                .thenReturn(Mono.just(false));
        // El gateway reporta solo el subconjunto existente (falta al menos un id).
        lenient().when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(existing));

        CapabilityUseCase useCase =
                new CapabilityUseCase(persistencePort, technologyGatewayPort);

        Capability input = new Capability(null, name, description, new ArrayList<>(requested));

        StepVerifier.create(useCase.registerCapability(input))
                .expectErrorSatisfies(error -> {
                    if (!(error instanceof TechnologiesNotFoundException ex)) {
                        throw new AssertionError(
                                "tipo de error inesperado: esperado TechnologiesNotFoundException"
                                        + " obtenido=" + error.getClass().getName());
                    }
                    if (!ex.getMissingIds().containsAll(missing)
                            || ex.getMissingIds().size() != missing.size()) {
                        throw new AssertionError(
                                "missingIds inesperados: esperado=" + missing
                                        + " obtenido=" + ex.getMissingIds());
                    }
                })
                .verify();

        verify(persistencePort, never()).save(any());
    }

    /**
     * Nombres válidos: 1-50 caracteres alfanuméricos (sin espacios de borde), de
     * modo que la regla de nombre siempre pase y solo pueda dispararse la regla de
     * existencia de tecnologías.
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
     * dispararse la regla de existencia de tecnologías.
     */
    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(90);
    }

    /**
     * Genera casos con una lista de identificadores {@code Long} distintos y
     * positivos de tamaño válido (3-20) junto con {@code dropCount}, el número de
     * identificadores a marcar como inexistentes (entre 1 y {@code tamaño - 1}),
     * garantizando así un subconjunto existente estricto (falta al menos uno).
     */
    @Provide
    Arbitrary<SubsetCase> missingSubsetCases() {
        Arbitrary<List<Long>> idLists = Arbitraries.longs().between(1L, 1_000_000L)
                .list()
                .uniqueElements()
                .ofMinSize(3)
                .ofMaxSize(20);

        return idLists.flatMap(ids ->
                Arbitraries.integers().between(1, ids.size() - 1)
                        .map(dropCount -> new SubsetCase(ids, dropCount)));
    }

    /**
     * Caso de prueba: la lista de identificadores solicitados y cuántos de ellos
     * (los primeros {@code dropCount}) el gateway reportará como inexistentes.
     */
    record SubsetCase(List<Long> requestedIds, int dropCount) {
    }
}
