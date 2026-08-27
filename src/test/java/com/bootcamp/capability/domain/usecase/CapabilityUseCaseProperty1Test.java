package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based test para la primera propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 1 — Validates: Requirements 1.1,
 * 2.4, 3.3, 4.3, 5.3, 6.2, 7.3
 *
 * <p><b>Property 1: Registro válido conserva datos normalizados y persiste.</b>
 * Para todo nombre válido (1-50 tras trim), descripción válida (1-90 tras trim)
 * y conjunto de 3-20 ids distintos, cuando el nombre no existe y todos los ids
 * existen, {@code registerCapability} emite un {@link Capability} con
 * {@code name}/{@code description} normalizados (trim) y los mismos ids, e
 * invoca {@code save} exactamente una vez.
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de
 * cada intento (jqwik no usa la extensión JUnit de Mockito). El stub de
 * {@code save} devuelve una capacidad con un id asignado que refleja los campos
 * de entrada.
 */
class CapabilityUseCaseProperty1Test {

    private static final long ASSIGNED_ID = 999L;

    @Property(tries = 200)
    void validRegistrationNormalizesDataAndPersists(
            @ForAll("paddedNames") String rawName,
            @ForAll("paddedDescriptions") String rawDescription,
            @ForAll("distinctIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(ids));
        // save echoes the input fields with an assigned id.
        when(persistencePort.save(any(Capability.class))).thenAnswer(invocation -> {
            Capability toSave = invocation.getArgument(0);
            return Mono.just(new Capability(
                    ASSIGNED_ID,
                    toSave.getName(),
                    toSave.getDescription(),
                    toSave.getTechnologyIds()));
        });

        CapabilityUseCase useCase =
                new CapabilityUseCase(persistencePort, technologyGatewayPort);

        String expectedName = rawName.trim();
        String expectedDescription = rawDescription.trim();

        Capability input = new Capability(null, rawName, rawDescription, new ArrayList<>(ids));

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(result -> {
                    if (!expectedName.equals(result.getName())) {
                        throw new AssertionError(
                                "name no normalizado: esperado='" + expectedName
                                        + "' obtenido='" + result.getName() + "'");
                    }
                    if (!expectedDescription.equals(result.getDescription())) {
                        throw new AssertionError(
                                "description no normalizada: esperado='" + expectedDescription
                                        + "' obtenido='" + result.getDescription() + "'");
                    }
                    if (!Set.copyOf(ids).equals(Set.copyOf(result.getTechnologyIds()))) {
                        throw new AssertionError(
                                "technologyIds distintos: esperado=" + ids
                                        + " obtenido=" + result.getTechnologyIds());
                    }
                    if (result.getTechnologyIds().size() != ids.size()) {
                        throw new AssertionError(
                                "cantidad de technologyIds distinta: esperado=" + ids.size()
                                        + " obtenido=" + result.getTechnologyIds().size());
                    }
                })
                .verifyComplete();

        verify(persistencePort, times(1)).save(any(Capability.class));
    }

    /**
     * Nombres cuyo valor tras {@code trim} tiene longitud 1-50, opcionalmente
     * rodeados de espacios en blanco de borde (que no alteran la longitud útil).
     */
    @Provide
    Arbitrary<String> paddedNames() {
        return trimmedTextWithPadding(1, 50);
    }

    /**
     * Descripciones cuyo valor tras {@code trim} tiene longitud 1-90,
     * opcionalmente rodeadas de espacios en blanco de borde.
     */
    @Provide
    Arbitrary<String> paddedDescriptions() {
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
        return Combinators.combine(leftPad, core, rightPad)
                .as((l, c, r) -> l + c + r);
    }

    /**
     * Conjunto de 3-20 identificadores {@code Long} distintos y positivos.
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
