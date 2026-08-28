package com.bootcamp.capability.domain.usecase;

import com.bootcamp.capability.domain.api.ICapabilityServicePort;
import com.bootcamp.capability.domain.exception.CapabilityAlreadyExistsException;
import com.bootcamp.capability.domain.exception.DomainErrorCode;
import com.bootcamp.capability.domain.exception.InvalidCapabilityDataException;
import com.bootcamp.capability.domain.exception.TechnologiesNotFoundException;
import com.bootcamp.capability.domain.exception.InvalidPageQueryException;
import com.bootcamp.capability.domain.exception.PageErrorCode;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.PagedResult;
import com.bootcamp.capability.domain.model.TechnologySummary;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private static final int MIN_PAGE = 0;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

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
     * Lista las capacidades de forma paginada y ordenada, componiendo un único
     * pipeline reactivo sin llamadas bloqueantes:
     * <ol>
     *   <li>valida el rango de {@code page}/{@code size} (Req 4.3-4.6);</li>
     *   <li>obtiene, en paralelo, la página desde la BD (ya ordenada y paginada)
     *       y el conteo total (Req 1.1, 1.2);</li>
     *   <li>si la página está vacía, omite el gateway y retorna un
     *       {@link PagedResult} vacío con la metadata coherente (Req 1.4, 5.6);</li>
     *   <li>en caso contrario, enriquece cada capacidad con los nombres de sus
     *       tecnologías mediante una única llamada por lotes (Req 5).</li>
     * </ol>
     *
     * <p>El orden emitido por {@code findPage} se preserva (Req 2/3/6). El error de
     * indisponibilidad del Technology_Service se propaga sin capturarse (Req 7.1).
     */
    @Override
    public Mono<PagedResult<CapabilityListItem>> listCapabilities(CapabilityPageQuery query) {
        return validateQuery(query)
                .flatMap(validQuery -> Mono.zip(
                                persistencePort.findPage(validQuery).collectList(),
                                persistencePort.countAll())
                        .flatMap(tuple -> {
                            List<Capability> pageContent = tuple.getT1();
                            long totalElements = tuple.getT2();
                            if (pageContent.isEmpty()) {
                                return Mono.just(new PagedResult<CapabilityListItem>(
                                        validQuery.getPage(), validQuery.getSize(),
                                        totalElements, List.of()));
                            }
                            return enrichWithTechnologies(pageContent)
                                    .map(items -> new PagedResult<>(
                                            validQuery.getPage(), validQuery.getSize(),
                                            totalElements, items));
                        }));
    }

    /**
     * Valida el rango de los parámetros de paginación en memoria (sin I/O):
     * {@code page < 0} -> {@code PAGE_NEGATIVE}; {@code size < 1} ->
     * {@code SIZE_TOO_SMALL}; {@code size > 100} -> {@code SIZE_TOO_LARGE}
     * (Req 4.3, 4.4, 4.5). Los valores de {@code sortBy}/{@code direction} ya
     * llegan resueltos a enum (o al default) desde la capa driving.
     *
     * @param query parámetros de consulta a validar.
     * @return un {@link Mono} que emite el query válido, o un
     *         {@link InvalidPageQueryException} si algún rango falla.
     */
    private Mono<CapabilityPageQuery> validateQuery(CapabilityPageQuery query) {
        return Mono.defer(() -> {
            if (query.getPage() < MIN_PAGE) {
                return Mono.error(new InvalidPageQueryException(PageErrorCode.PAGE_NEGATIVE));
            }
            if (query.getSize() < MIN_SIZE) {
                return Mono.error(new InvalidPageQueryException(PageErrorCode.SIZE_TOO_SMALL));
            }
            if (query.getSize() > MAX_SIZE) {
                return Mono.error(new InvalidPageQueryException(PageErrorCode.SIZE_TOO_LARGE));
            }
            return Mono.just(query);
        });
    }

    /**
     * Recupera las capacidades por id y las enriquece con sus tecnologías (id y
     * nombre) reutilizando el mismo enriquecimiento por lotes del listado (evita
     * N+1). Si no se solicita ningún id o ninguna capacidad existe, emite vacío.
     */
    @Override
    public Flux<CapabilityListItem> findCapabilitiesByIds(Collection<Long> ids) {
        return persistencePort.findByIds(ids)
                .collectList()
                .flatMapMany(capabilities -> capabilities.isEmpty()
                        ? Flux.empty()
                        : enrichWithTechnologies(capabilities).flatMapMany(Flux::fromIterable));
    }

    /**
     * Enriquece las capacidades de la página con los nombres de sus tecnologías,
     * evitando el problema N+1: recolecta todos los {@code technologyId} distintos
     * de la página (preservando el orden de aparición) y hace una única llamada por
     * lotes al gateway; con el mapa {@code id -> TechnologySummary} resultante,
     * asocia a cada capacidad únicamente las tecnologías resueltas (omitiendo las
     * no devueltas por el service, Req 5.5), preservando el orden de la página.
     *
     * @param page capacidades de la página (no vacía).
     * @return un {@link Mono} con la lista de {@link CapabilityListItem} enriquecidos.
     */
    private Mono<List<CapabilityListItem>> enrichWithTechnologies(List<Capability> page) {
        Set<Long> distinctIds = page.stream()
                .flatMap(c -> c.getTechnologyIds() == null
                        ? java.util.stream.Stream.<Long>empty()
                        : c.getTechnologyIds().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return technologyGatewayPort.findTechnologiesByIds(distinctIds)
                .collectMap(TechnologySummary::getId, Function.identity())
                .map(byId -> page.stream()
                        .map(c -> new CapabilityListItem(
                                c.getId(), c.getName(), c.getDescription(),
                                resolveTechnologies(c, byId)))
                        .toList());
    }

    private List<TechnologySummary> resolveTechnologies(Capability capability,
                                                        Map<Long, TechnologySummary> byId) {
        List<Long> ids = capability.getTechnologyIds();
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
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
