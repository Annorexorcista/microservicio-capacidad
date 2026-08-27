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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test para la tercera propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 3 — Validates: Requirements 3.3, 4.4
 *
 * <p><b>Property 3: La normalización por trim es idempotente.</b>
 * Para todo nombre válido (1-50 tras trim) y descripción válida (1-90 tras
 * trim), rodeados de espacios en blanco arbitrarios (espacios, tabuladores y
 * saltos de línea) en los bordes, {@code registerCapability} emite un
 * {@link Capability} cuyo {@code name}/{@code description} son exactamente los
 * valores sin espacios de borde. Como el núcleo generado ya está recortado,
 * recortar de nuevo un valor recortado produce el mismo valor: la normalización
 * por {@code trim} es idempotente ({@code core.trim() == core}).
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de
 * cada intento (jqwik no usa la extensión JUnit de Mockito). El stub de
 * {@code existsByNameIgnoreCase} devuelve {@code false} (el nombre no existe);
 * el gateway devuelve todos los ids solicitados como existentes; y {@code save}
 * refleja los campos de la capacidad de entrada asignándole un id.
 */
class CapabilityUseCaseProperty3Test {

    private static final long ASSIGNED_ID = 999L;

    @Property(tries = 200)
    void trimNormalizationIsIdempotent(
            @ForAll("nameCores") String nameCore,
            @ForAll("descriptionCores") String descriptionCore,
            @ForAll("whitespacePaddings") Padding namePadding,
            @ForAll("whitespacePaddings") Padding descriptionPadding,
            @ForAll("distinctIdSets") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        when(persistencePort.existsByNameIgnoreCase(anyString())).thenReturn(Mono.just(false));
        when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(ids));
        // save refleja los campos de entrada con un id asignado.
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

        String rawName = namePadding.left + nameCore + namePadding.right;
        String rawDescription =
                descriptionPadding.left + descriptionCore + descriptionPadding.right;

        // El núcleo ya está recortado: trim de un valor recortado devuelve el mismo
        // valor (idempotencia).
        String expectedName = nameCore.trim();
        String expectedDescription = descriptionCore.trim();

        Capability input = new Capability(null, rawName, rawDescription, new ArrayList<>(ids));

        StepVerifier.create(useCase.registerCapability(input))
                .assertNext(result -> {
                    if (!expectedName.equals(result.getName())) {
                        throw new AssertionError(
                                "name no normalizado por trim: esperado='" + expectedName
                                        + "' obtenido='" + result.getName() + "'");
                    }
                    if (!expectedDescription.equals(result.getDescription())) {
                        throw new AssertionError(
                                "description no normalizada por trim: esperado='"
                                        + expectedDescription
                                        + "' obtenido='" + result.getDescription() + "'");
                    }
                    // Idempotencia explícita: recortar el resultado ya recortado no cambia nada.
                    if (!result.getName().equals(result.getName().trim())) {
                        throw new AssertionError(
                                "name no idempotente bajo trim: '" + result.getName() + "'");
                    }
                    if (!result.getDescription().equals(result.getDescription().trim())) {
                        throw new AssertionError(
                                "description no idempotente bajo trim: '"
                                        + result.getDescription() + "'");
                    }
                })
                .verifyComplete();
    }

    /**
     * Núcleos de nombre: 1-50 caracteres alfanuméricos (sin espacios internos ni
     * de borde), de modo que su longitud tras {@code trim} permanezca en 1-50.
     */
    @Provide
    Arbitrary<String> nameCores() {
        return alphanumericCore(1, 50);
    }

    /**
     * Núcleos de descripción: 1-90 caracteres alfanuméricos (sin espacios), de
     * modo que su longitud tras {@code trim} permanezca en 1-90.
     */
    @Provide
    Arbitrary<String> descriptionCores() {
        return alphanumericCore(1, 90);
    }

    /**
     * Genera un texto de longitud {@code min}-{@code max} compuesto solo por
     * letras y dígitos, sin espacios en blanco, de modo que {@code trim} sobre él
     * sea la identidad.
     */
    private Arbitrary<String> alphanumericCore(int min, int max) {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(min)
                .ofMaxLength(max);
    }

    /**
     * Padding de espacios en blanco arbitrarios (espacios, tabuladores, saltos de
     * línea y retornos de carro) en los bordes izquierdo y derecho, de longitud
     * 0-4 cada uno. No altera la longitud útil tras {@code trim}.
     */
    @Provide
    Arbitrary<Padding> whitespacePaddings() {
        Arbitrary<String> ws = Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r')
                .ofMinLength(0)
                .ofMaxLength(4);
        return Combinators.combine(ws, ws).as(Padding::new);
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

    /**
     * Par de cadenas de relleno (borde izquierdo y derecho) compuestas por
     * espacios en blanco arbitrarios.
     */
    static final class Padding {
        final String left;
        final String right;

        Padding(String left, String right) {
            this.left = left;
            this.right = right;
        }
    }
}
