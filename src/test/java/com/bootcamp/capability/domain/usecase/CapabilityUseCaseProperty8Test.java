package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.exception.DomainErrorCode;
import com.bootcamp.capability.domain.exception.InvalidCapabilityDataException;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Property-based test para la octava propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 8 — Validates: Requirements 6.1
 *
 * <p><b>Property 8: Tecnologías repetidas rechazadas sin persistir.</b> Para toda
 * lista de ids que contenga al menos un identificador duplicado, con un nombre
 * válido (1-50) y una descripción válida (1-90),
 * {@code registerCapability} emite un {@link InvalidCapabilityDataException} cuyo
 * código es {@link DomainErrorCode#TECHNOLOGIES_DUPLICATED} y nunca invoca
 * {@code save}.
 *
 * <p><b>Orden de validación (verificado en {@link CapabilityUseCase#validate}):</b>
 * la detección de duplicados (comparar el tamaño de la lista de ids no nulos con
 * el de sus distintos) ocurre <b>antes</b> de la validación de cantidad
 * (demasiado pocos / demasiados). Por tanto, cualquier lista con un duplicado
 * dispara {@code TECHNOLOGIES_DUPLICATED} sin importar el tamaño total. El
 * generador construye una base de ids distintos y le añade al menos un valor
 * repetido, garantizando que siempre exista un duplicado.
 *
 * <p>Los puertos {@link ICapabilityPersistencePort} e
 * {@link ITechnologyGatewayPort} se mockean manualmente con Mockito dentro de
 * cada intento (jqwik no usa la extensión JUnit de Mockito). Los stubs de
 * {@code existsByNameIgnoreCase} y del gateway se configuran de forma laxa
 * ({@code lenient}) porque no deberían alcanzarse: la validación de duplicados
 * corta el pipeline antes. Se verifica que {@code save} nunca se invoca.
 */
class CapabilityUseCaseProperty8Test {

    @Property(tries = 200)
    void duplicatedTechnologiesAreRejectedWithoutPersisting(
            @ForAll("validNames") String name,
            @ForAll("validDescriptions") String description,
            @ForAll("idListsWithDuplicate") List<Long> ids) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Stubs laxos: no deberían alcanzarse, la regla de no repetición corta antes.
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
                    if (ex.getCode() != DomainErrorCode.TECHNOLOGIES_DUPLICATED) {
                        throw new AssertionError(
                                "código de error inesperado: esperado TECHNOLOGIES_DUPLICATED"
                                        + " obtenido=" + ex.getCode());
                    }
                })
                .verify();

        verify(persistencePort, never()).save(any());
    }

    /**
     * Nombres válidos: 1-50 caracteres alfanuméricos (sin espacios de borde), de
     * modo que la regla de nombre siempre pase y solo pueda dispararse la regla
     * de no repetición de tecnologías.
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
     * dispararse la regla de no repetición de tecnologías.
     */
    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(90);
    }

    /**
     * Genera listas de {@code Long} que contienen al menos un identificador
     * repetido. Se parte de una base de ids distintos (1-20 elementos) y se le
     * añaden 1 o más valores tomados de esa misma base (por tanto duplicados);
     * finalmente la lista se baraja para que el duplicado no quede siempre al
     * final. Como {@code validate} detecta duplicados antes de contar la cantidad,
     * el único fallo posible es {@code TECHNOLOGIES_DUPLICATED} (Req 6.1),
     * independientemente del tamaño total resultante.
     */
    @Provide
    Arbitrary<List<Long>> idListsWithDuplicate() {
        Arbitrary<List<Long>> distinctBase = Arbitraries.longs().between(1L, 1_000_000L)
                .list()
                .uniqueElements()
                .ofMinSize(1)
                .ofMaxSize(20);

        // Cuántas repeticiones extra añadir (al menos una para garantizar el duplicado).
        Arbitrary<Integer> extraCount = Arbitraries.integers().between(1, 10);

        return Combinators.combine(distinctBase, extraCount)
                .as((base, extra) -> {
                    List<Long> result = new ArrayList<>(base);
                    for (int i = 0; i < extra; i++) {
                        // Tomar un valor ya presente en la base -> garantiza un duplicado.
                        Long duplicate = base.get(i % base.size());
                        result.add(duplicate);
                    }
                    Collections.shuffle(result);
                    return result;
                });
    }
}
