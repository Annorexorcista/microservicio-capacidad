package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.adapter;

import com.bootcamp.capability.domain.exception.CapabilityAlreadyExistsException;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityEntity;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityTechnologyEntity;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.mapper.CapabilityEntityMapper;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityRepository;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityTechnologyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Adaptador driven que implementa el puerto de salida {@link ICapabilityPersistencePort}
 * usando Spring Data R2DBC.
 *
 * <p>Persiste en dos tablas dentro de una transacción reactiva: primero
 * {@code capability} (obteniendo el id autogenerado por MySQL) y luego las filas
 * de la tabla puente {@code capability_technology} (una por cada tecnología
 * asociada). Ambas escrituras se envuelven en un {@link TransactionalOperator}
 * mediante {@code .as(transactionalOperator::transactional)}, de forma que
 * fallen o confirmen de manera atómica (Req 1.2).
 *
 * <p>La entidad {@link CapabilityTechnologyEntity} tiene clave primaria compuesta
 * y no un {@code @Id} de una sola columna, por lo que el guardado de sus filas se
 * hace con {@link R2dbcEntityTemplate#insert(Object)} (que siempre ejecuta un
 * INSERT) en lugar de {@code saveAll}, que no podría inferir que la fila es nueva.
 *
 * <p>No se anota con {@code @Component}: el wiring hexagonal se realiza en
 * {@code BeanConfiguration} (tarea 8) para mantener el dominio y el adaptador
 * libres de acoplamiento a la configuración de Spring. Es un flujo totalmente
 * reactivo, sin llamadas bloqueantes ({@code .block()}).
 */
public class CapabilityPersistenceAdapter implements ICapabilityPersistencePort {

    private final ICapabilityRepository capabilityRepository;
    private final ICapabilityTechnologyRepository capabilityTechnologyRepository;
    private final CapabilityEntityMapper mapper;
    private final TransactionalOperator transactionalOperator;
    private final R2dbcEntityTemplate entityTemplate;

    public CapabilityPersistenceAdapter(ICapabilityRepository capabilityRepository,
                                        ICapabilityTechnologyRepository capabilityTechnologyRepository,
                                        CapabilityEntityMapper mapper,
                                        TransactionalOperator transactionalOperator,
                                        R2dbcEntityTemplate entityTemplate) {
        this.capabilityRepository = capabilityRepository;
        this.capabilityTechnologyRepository = capabilityTechnologyRepository;
        this.mapper = mapper;
        this.transactionalOperator = transactionalOperator;
        this.entityTemplate = entityTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delega directamente en la derived query del repositorio, que compara el
     * nombre sin distinción entre mayúsculas y minúsculas.
     */
    @Override
    public Mono<Boolean> existsByNameIgnoreCase(String normalizedName) {
        return capabilityRepository.existsByNameIgnoreCase(normalizedName);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Persiste la capacidad y sus asociaciones de forma transaccional: guarda
     * la entidad {@code capability} para obtener el id generado, inserta una fila
     * en {@code capability_technology} por cada tecnología asociada y reconstruye
     * el modelo de dominio con el id y los mismos identificadores de tecnología.
     * Una {@link DataIntegrityViolationException} (por ejemplo, condición de
     * carrera sobre la restricción UNIQUE del nombre) se reasigna a
     * {@link CapabilityAlreadyExistsException} para reforzar la respuesta 409.
     */
    @Override
    public Mono<Capability> save(Capability capability) {
        Mono<Capability> pipeline = capabilityRepository
                .save(mapper.toEntity(capability))
                .flatMap(saved -> {
                    List<Long> technologyIds = capability.getTechnologyIds();
                    return Flux.fromIterable(technologyIds)
                            .concatMap(techId -> entityTemplate.insert(
                                    new CapabilityTechnologyEntity(saved.getId(), techId)))
                            .then(Mono.just(mapper.toDomain(saved, technologyIds)));
                })
                .onErrorMap(DataIntegrityViolationException.class,
                        ex -> new CapabilityAlreadyExistsException(capability.getName()));

        return pipeline.as(transactionalOperator::transactional);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delega en {@code count()} del repositorio reactivo.
     */
    @Override
    public Mono<Long> countAll() {
        return capabilityRepository.count();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ejecuta la consulta paginada y ordenada en la base de datos (LIMIT/OFFSET
     * y ORDER BY resueltos en SQL). Para {@code NAME} ordena por {@code c.name};
     * para {@code TECHNOLOGY_COUNT} calcula el conteo con {@code LEFT JOIN} sobre
     * la tabla puente, {@code GROUP BY} por capacidad y {@code COUNT}, ordenando
     * por ese conteo. El {@code sortBy}/{@code direction} NO se interpolan como
     * texto libre: se traducen desde los enums a fragmentos SQL de una lista
     * blanca, evitando inyección SQL.
     *
     * <p>Tras obtener las capacidades de la página (en el orden de la consulta),
     * resuelve los {@code technologyIds} de cada una con {@code findByCapabilityId}
     * usando {@code concatMap} para preservar ese orden.
     */
    @Override
    public Flux<Capability> findPage(CapabilityPageQuery query) {
        long offset = (long) query.getPage() * query.getSize();
        String sql = buildPageSql(query.getSortBy(), query.getDirection());
        return entityTemplate.getDatabaseClient().sql(sql)
                .bind("size", query.getSize())
                .bind("offset", offset)
                .map((row, meta) -> new CapabilityEntity(
                        row.get("id", Long.class),
                        row.get("name", String.class),
                        row.get("description", String.class)))
                .all()
                .concatMap(entity -> capabilityTechnologyRepository.findByCapabilityId(entity.getId())
                        .map(CapabilityTechnologyEntity::getTechnologyId)
                        .collectList()
                        .map(ids -> mapper.toDomain(entity, ids)));
    }

    /**
     * Construye el SQL de la página traduciendo {@code sortBy}/{@code direction}
     * a fragmentos fijos de una lista blanca (sin interpolar entrada de usuario).
     */
    /**
     * {@inheritDoc}
     *
     * <p>Recupera las entidades por id con {@code findAllById} (derived del
     * repositorio) y resuelve los {@code technologyIds} de cada una con
     * {@code findByCapabilityId}, igual que {@link #findPage}. Devuelve solo las
     * capacidades existentes; un id inexistente simplemente no aparece.
     */
    @Override
    public Flux<Capability> findByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        return capabilityRepository.findAllById(ids)
                .flatMap(entity -> capabilityTechnologyRepository.findByCapabilityId(entity.getId())
                        .map(CapabilityTechnologyEntity::getTechnologyId)
                        .collectList()
                        .map(techIds -> mapper.toDomain(entity, techIds)));
    }

    private String buildPageSql(CapabilitySortBy sortBy, CapabilitySortDirection direction) {
        String dir = direction == CapabilitySortDirection.DESC ? "DESC" : "ASC";
        if (sortBy == CapabilitySortBy.TECHNOLOGY_COUNT) {
            return "SELECT c.id, c.name, c.description, COUNT(ct.technology_id) AS tech_count "
                    + "FROM capability c "
                    + "LEFT JOIN capability_technology ct ON ct.capability_id = c.id "
                    + "GROUP BY c.id, c.name, c.description "
                    + "ORDER BY tech_count " + dir + ", c.id " + dir + " "
                    + "LIMIT :size OFFSET :offset";
        }
        return "SELECT c.id, c.name, c.description "
                + "FROM capability c "
                + "ORDER BY c.name " + dir + ", c.id " + dir + " "
                + "LIMIT :size OFFSET :offset";
    }
}
