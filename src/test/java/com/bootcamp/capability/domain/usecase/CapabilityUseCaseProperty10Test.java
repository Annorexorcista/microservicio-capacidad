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
 * Property-based test para la décima propiedad de correctitud del caso de uso
 * {@link CapabilityUseCase}.
 *
 * <p>Feature: registrar-capacidades, Property 10 — Validates: Requirements 2.2, 3.4, 4.1, 4.2, 5.1, 5.2, 6.1, 7.2
 *
 * <p><b>Property 10: Invariante de no persistencia ante cualquier error.</b> Para
 * toda solicitud que falle <b>cualquiera</b> de las validaciones (longitudes de
 * nombre/descripción, obligatoriedad, cantidad de tecnologías, repetición de
 * tecnologías, unicidad del nombre o existencia de tecnologías),
 * {@code registerCapability} termina con <b>algún</b> error y {@code save}
 * <b>nunca</b> se invoca.
 *
 * <p>Este es el invariante general: en lugar de fijar una categoría de fallo, el
 * generador {@code failingCases} produce, en cada intento, un
 * {@link FailingCase} que cae aleatoriamente en una de las categorías de fallo
 * y que trae consigo la configuración de mocks necesaria para que esa —y solo
 * esa— regla falle. Se cubren, como mínimo:
 * <ul>
 *   <li>nombre vacío ({@code EMPTY_NAME});</li>
 *   <li>nombre demasiado largo &gt; 50 ({@code NAME_TOO_LONG});</li>
 *   <li>descripción vacía ({@code EMPTY_DESCRIPTION});</li>
 *   <li>descripción demasiado larga &gt; 90 ({@code DESCRIPTION_TOO_LONG});</li>
 *   <li>muy pocas tecnologías &lt; 3 ({@code TOO_FEW_TECHNOLOGIES});</li>
 *   <li>demasiadas tecnologías &gt; 20 ({@code TOO_MANY_TECHNOLOGIES});</li>
 *   <li>tecnologías duplicadas ({@code DUPLICATED_TECHNOLOGIES});</li>
 *   <li>nombre duplicado — {@code existsByNameIgnoreCase} devuelve
 *       {@code true} ({@code DUPLICATE_NAME});</li>
 *   <li>tecnología inexistente — el gateway devuelve un subconjunto estricto de
 *       los solicitados ({@code MISSING_TECHNOLOGY}).</li>
 * </ul>
 *
 * <p>Para cada caso, el nombre, la descripción y los ids se mantienen válidos
 * <b>excepto</b> en el campo bajo prueba, de modo que la regla que falla sea
 * exactamente la buscada. Los stubs se configuran de forma laxa
 * ({@code lenient}): en los fallos sintácticos (validación en memoria) los
 * puertos no se alcanzan; en {@code DUPLICATE_NAME} se stubea
 * {@code existsByNameIgnoreCase -> Mono.just(true)}; en {@code MISSING_TECHNOLOGY}
 * el gateway devuelve solo un subconjunto de los ids solicitados.
 *
 * <p>La aserción con {@link StepVerifier} verifica únicamente que el pipeline
 * termina con <b>algún</b> error ({@code expectError()} sin tipo específico), y
 * —el punto crucial de la propiedad— que {@code save} nunca se invoca en ningún
 * caso.
 */
class CapabilityUseCaseProperty10Test {

    /** Categorías de fallo cubiertas por el generador. */
    private enum FailureKind {
        EMPTY_NAME,
        NAME_TOO_LONG,
        EMPTY_DESCRIPTION,
        DESCRIPTION_TOO_LONG,
        TOO_FEW_TECHNOLOGIES,
        TOO_MANY_TECHNOLOGIES,
        DUPLICATED_TECHNOLOGIES,
        DUPLICATE_NAME,
        MISSING_TECHNOLOGY
    }

    @Property(tries = 300)
    void anyValidationFailureTerminatesWithErrorAndNeverPersists(
            @ForAll("failingCases") FailingCase failingCase) {

        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort technologyGatewayPort = mock(ITechnologyGatewayPort.class);

        // Por defecto: nombre único y gateway que confirma todos los ids solicitados.
        // Cada caso puede sobrescribir estos stubs para provocar su fallo específico.
        lenient().when(persistencePort.existsByNameIgnoreCase(anyString()))
                .thenReturn(Mono.just(failingCase.nameAlreadyExists()));
        lenient().when(technologyGatewayPort.findExistingTechnologyIds(anyCollection()))
                .thenReturn(Flux.fromIterable(failingCase.existingIds()));

        CapabilityUseCase useCase =
                new CapabilityUseCase(persistencePort, technologyGatewayPort);

        Capability input = new Capability(
                null,
                failingCase.name(),
                failingCase.description(),
                new ArrayList<>(failingCase.requestedIds()));

        // El invariante: el pipeline termina con ALGÚN error (sin fijar tipo).
        StepVerifier.create(useCase.registerCapability(input))
                .expectError()
                .verify();

        // CRUCIAL: nunca se persiste ante ningún error de validación.
        verify(persistencePort, never()).save(any());
    }

    /**
     * Genera un caso fallido eligiendo aleatoriamente una de las categorías de
     * {@link FailureKind}. Construye nombre, descripción e ids válidos y luego
     * corrompe únicamente el aspecto correspondiente a la categoría, ajustando la
     * configuración de mocks ({@code nameAlreadyExists}, {@code existingIds})
     * cuando el fallo depende de I/O (unicidad o existencia).
     */
    @Provide
    Arbitrary<FailingCase> failingCases() {
        Arbitrary<FailureKind> kinds = Arbitraries.of(FailureKind.values());

        // Piezas válidas reutilizables.
        Arbitrary<String> validName = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> validDescription = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1).ofMaxLength(90);
        Arbitrary<List<Long>> validIds = Arbitraries.longs().between(1L, 1_000_000L)
                .list().uniqueElements().ofMinSize(3).ofMaxSize(20);

        // Piezas inválidas.
        Arbitrary<String> blankName = Arbitraries.of("", " ", "   ", "\t", "  \n ");
        Arbitrary<String> longName = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz")
                .ofMinLength(51).ofMaxLength(120);
        Arbitrary<String> blankDescription = Arbitraries.of("", " ", "   ", "\t", "  \n ");
        Arbitrary<String> longDescription = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz")
                .ofMinLength(91).ofMaxLength(160);
        Arbitrary<List<Long>> tooFewIds = Arbitraries.longs().between(1L, 1_000_000L)
                .list().uniqueElements().ofMinSize(0).ofMaxSize(2);
        Arbitrary<List<Long>> tooManyIds = Arbitraries.longs().between(1L, 1_000_000L)
                .list().uniqueElements().ofMinSize(21).ofMaxSize(40);

        return kinds.flatMap(kind -> switch (kind) {
            case EMPTY_NAME -> Combinators.combine(blankName, validDescription, validIds)
                    .as((name, desc, ids) ->
                            FailingCase.builder(name, desc, ids).build());

            case NAME_TOO_LONG -> Combinators.combine(longName, validDescription, validIds)
                    .as((name, desc, ids) ->
                            FailingCase.builder(name, desc, ids).build());

            case EMPTY_DESCRIPTION -> Combinators.combine(validName, blankDescription, validIds)
                    .as((name, desc, ids) ->
                            FailingCase.builder(name, desc, ids).build());

            case DESCRIPTION_TOO_LONG -> Combinators.combine(validName, longDescription, validIds)
                    .as((name, desc, ids) ->
                            FailingCase.builder(name, desc, ids).build());

            case TOO_FEW_TECHNOLOGIES -> Combinators.combine(validName, validDescription, tooFewIds)
                    .as((name, desc, ids) ->
                            FailingCase.builder(name, desc, ids).build());

            case TOO_MANY_TECHNOLOGIES -> Combinators.combine(validName, validDescription, tooManyIds)
                    .as((name, desc, ids) ->
                            FailingCase.builder(name, desc, ids).build());

            case DUPLICATED_TECHNOLOGIES -> Combinators.combine(validName, validDescription, validIds)
                    .as((name, desc, ids) -> {
                        List<Long> withDup = new ArrayList<>(ids);
                        withDup.add(ids.get(0)); // fuerza un duplicado
                        Collections.shuffle(withDup);
                        return FailingCase.builder(name, desc, withDup).build();
                    });

            // Fallo por unicidad: datos válidos, pero existsByNameIgnoreCase -> true.
            case DUPLICATE_NAME -> Combinators.combine(validName, validDescription, validIds)
                    .as((name, desc, ids) ->
                            FailingCase.builder(name, desc, ids)
                                    .nameAlreadyExists(true)
                                    .existingIds(new ArrayList<>(ids)) // gateway confirmaría todos
                                    .build());

            // Fallo por existencia: gateway devuelve un subconjunto estricto (falta >= 1).
            case MISSING_TECHNOLOGY -> Combinators.combine(validName, validDescription, validIds)
                    .as((name, desc, ids) -> {
                        // Omitir el primer id: subconjunto existente estricto.
                        List<Long> existing = new ArrayList<>(ids.subList(1, ids.size()));
                        return FailingCase.builder(name, desc, ids)
                                .existingIds(existing)
                                .build();
                    });
        });
    }

    /**
     * Caso fallido: la entrada a registrar más la configuración de mocks
     * necesaria para que la categoría de fallo correspondiente se dispare.
     *
     * @param name              nombre de la capacidad (válido salvo en casos de nombre).
     * @param description       descripción (válida salvo en casos de descripción).
     * @param requestedIds      ids solicitados (válidos salvo en casos de cantidad/repetición).
     * @param nameAlreadyExists valor que devuelve {@code existsByNameIgnoreCase}.
     * @param existingIds       ids que el gateway reporta como existentes.
     */
    record FailingCase(String name,
                       String description,
                       List<Long> requestedIds,
                       boolean nameAlreadyExists,
                       List<Long> existingIds) {

        static Builder builder(String name, String description, List<Long> requestedIds) {
            return new Builder(name, description, requestedIds);
        }

        /** Builder con valores por defecto "sanos" (nombre único, gateway confirma todo). */
        static final class Builder {
            private final String name;
            private final String description;
            private final List<Long> requestedIds;
            private boolean nameAlreadyExists = false;
            private List<Long> existingIds;

            Builder(String name, String description, List<Long> requestedIds) {
                this.name = name;
                this.description = description;
                this.requestedIds = requestedIds;
                // Por defecto, el gateway confirma exactamente lo solicitado.
                this.existingIds = new ArrayList<>(requestedIds);
            }

            Builder nameAlreadyExists(boolean value) {
                this.nameAlreadyExists = value;
                return this;
            }

            Builder existingIds(List<Long> value) {
                this.existingIds = value;
                return this;
            }

            FailingCase build() {
                return new FailingCase(name, description, requestedIds, nameAlreadyExists, existingIds);
            }
        }
    }
}
