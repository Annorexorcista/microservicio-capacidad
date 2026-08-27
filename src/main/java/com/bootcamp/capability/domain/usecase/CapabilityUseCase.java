package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.api.ICapabilityServicePort;
import com.bootcamp.capability.domain.exception.CapabilityAlreadyExistsException;
import com.bootcamp.capability.domain.exception.DomainErrorCode;
import com.bootcamp.capability.domain.exception.InvalidCapabilityDataException;
import com.bootcamp.capability.domain.exception.TechnologiesNotFoundException;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Caso de uso del dominio para el registro de capacidades.
 *
 * <p>Implementa {@link ICapabilityServicePort} y concentra las reglas de negocio
 * como un pipeline reactivo. Cada paso transforma/valida produciendo un
 * {@link Mono} de éxito o un {@code Mono.error(...)} de fallo, de modo que un
 * error corta el pipeline sin persistir nada. No usa {@code .block()}.
 *
 * <p>Depende de dos puertos de salida: {@link ICapabilityPersistencePort} para la
 * persistencia y {@link ITechnologyGatewayPort} para validar la existencia de las
 * tecnologías asociadas. La lógica de esos puertos se compone en tareas posteriores.
 */
public class CapabilityUseCase implements ICapabilityServicePort {

    private static final int NAME_MAX_LENGTH = 50;
    private static final int DESCRIPTION_MAX_LENGTH = 90;
    private static final int MIN_TECHNOLOGIES = 3;
    private static final int MAX_TECHNOLOGIES = 20;

    private final ICapabilityPersistencePort persistencePort;
    private final ITechnologyGatewayPort technologyGatewayPort;

    public CapabilityUseCase(ICapabilityPersistencePort persistencePort,
                             ITechnologyGatewayPort technologyGatewayPort) {
        this.persistencePort = persistencePort;
        this.technologyGatewayPort = technologyGatewayPort;
    }

    @Override
    public Mono<Capability> registerCapability(Capability capability) {
        return validate(capability)
                .flatMap(this::ensureNameIsUnique)
                .flatMap(this::ensureTechnologiesExist)
                .flatMap(persistencePort::save);
    }

    /**
     * Normaliza y valida sintácticamente la capacidad en memoria (sin I/O):
     * <ol>
     *   <li>trim de nombre y descripción;</li>
     *   <li>nombre obligatorio y de longitud ≤ 50 (Req 3.1, 3.2);</li>
     *   <li>descripción obligatoria y de longitud ≤ 90 (Req 4.1, 4.2, 4.4);</li>
     *   <li>normaliza los ids de tecnología descartando nulls y detecta repetidos
     *       comparando el tamaño de la lista con el de los distintos (Req 6.1);</li>
     *   <li>valida la cantidad de distintos: mínimo 3 y máximo 20 (Req 5.1, 5.2);</li>
     *   <li>emite un {@link Capability} normalizado (trim + ids distintos, {@code id == null}).</li>
     * </ol>
     *
     * @param capability capacidad de entrada tal como llega desde la capa driving.
     * @return un {@link Mono} que emite la capacidad normalizada o un
     *         {@link InvalidCapabilityDataException} si alguna regla falla.
     */
    private Mono<Capability> validate(Capability capability) {
        return Mono.defer(() -> {
            String name = capability.getName() == null ? null : capability.getName().trim();
            String description =
                    capability.getDescription() == null ? null : capability.getDescription().trim();

            if (name == null || name.isEmpty()) {
                return Mono.error(new InvalidCapabilityDataException(DomainErrorCode.NAME_REQUIRED));
            }
            if (name.length() > NAME_MAX_LENGTH) {
                return Mono.error(new InvalidCapabilityDataException(DomainErrorCode.NAME_TOO_LONG));
            }

            if (description == null || description.isEmpty()) {
                return Mono.error(
                        new InvalidCapabilityDataException(DomainErrorCode.DESCRIPTION_REQUIRED));
            }
            if (description.length() > DESCRIPTION_MAX_LENGTH) {
                return Mono.error(
                        new InvalidCapabilityDataException(DomainErrorCode.DESCRIPTION_TOO_LONG));
            }

            List<Long> rawIds = capability.getTechnologyIds();
            List<Long> nonNullIds = rawIds == null
                    ? List.of()
                    : rawIds.stream().filter(Objects::nonNull).toList();

            List<Long> distinctIds = nonNullIds.stream().distinct().toList();

            if (distinctIds.size() != nonNullIds.size()) {
                return Mono.error(
                        new InvalidCapabilityDataException(DomainErrorCode.TECHNOLOGIES_DUPLICATED));
            }

            if (distinctIds.size() < MIN_TECHNOLOGIES) {
                return Mono.error(
                        new InvalidCapabilityDataException(DomainErrorCode.TECHNOLOGIES_TOO_FEW));
            }
            if (distinctIds.size() > MAX_TECHNOLOGIES) {
                return Mono.error(
                        new InvalidCapabilityDataException(DomainErrorCode.TECHNOLOGIES_TOO_MANY));
            }

            return Mono.just(new Capability(null, name, description, distinctIds));
        });
    }

    /**
     * Valida la unicidad del nombre normalizado consultando al puerto de
     * persistencia con una comparación sin distinción entre mayúsculas y
     * minúsculas (Req 2.1). Si ya existe una capacidad con el mismo nombre, el
     * registro se rechaza sin persistir dato alguno (Req 2.2); en caso contrario,
     * el proceso continúa (Req 2.4).
     *
     * @param capability capacidad ya normalizada por {@link #validate(Capability)}.
     * @return un {@link Mono} que emite la capacidad si el nombre es único, o un
     *         {@link CapabilityAlreadyExistsException} si ya está registrado.
     */
    private Mono<Capability> ensureNameIsUnique(Capability capability) {
        return persistencePort.existsByNameIgnoreCase(capability.getName())
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(new CapabilityAlreadyExistsException(capability.getName()))
                        : Mono.just(capability));
    }

    /**
     * Valida que todas las tecnologías asociadas existan en el Technology_Service,
     * consultando el gateway (Req 7.1). Calcula los identificadores faltantes
     * comparando los solicitados contra el conjunto de existentes devuelto por el
     * gateway; si hay al menos uno faltante, rechaza el registro con
     * {@link TechnologiesNotFoundException} sin persistir (Req 7.2). Si todos
     * existen, continúa el proceso (Req 7.3).
     *
     * <p>La indisponibilidad del Technology_Service se traduce a
     * {@code TechnologyValidationUnavailableException} en el adaptador del gateway
     * (vía {@code onErrorMap}); aquí simplemente se propaga el error sin
     * capturarlo.
     *
     * @param capability capacidad ya normalizada y con nombre único.
     * @return un {@link Mono} que emite la capacidad si todas las tecnologías
     *         existen, o un error que corta el pipeline.
     */
    private Mono<Capability> ensureTechnologiesExist(Capability capability) {
        List<Long> requested = capability.getTechnologyIds();
        return technologyGatewayPort.findExistingTechnologyIds(requested)
                .collectList()
                .flatMap(existing -> {
                    Set<Long> existingSet = new HashSet<>(existing);
                    List<Long> missing = requested.stream()
                            .filter(id -> !existingSet.contains(id))
                            .toList();
                    return missing.isEmpty()
                            ? Mono.just(capability)
                            : Mono.error(new TechnologiesNotFoundException(missing));
                });
    }
}
