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

    @Override
    public Mono<Boolean> existsByNameIgnoreCase(String normalizedName) {
        return capabilityRepository.existsByNameIgnoreCase(normalizedName);
    }

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

    @Override
    public Mono<Long> countAll() {
        return capabilityRepository.count();
    }

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

    @Override
    public Flux<Long> deleteByIdsReturningOrphanTechnologyIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        Mono<java.util.List<Long>> pipeline = collectCandidateTechnologyIds(ids)
                // 1. technologyId distintos asociados a las capacidades a borrar
                .flatMap(candidates ->
                        // 2. borrar asociaciones y capacidades
                        capabilityTechnologyRepository.deleteByCapabilityIdIn(ids)
                                .then(capabilityRepository.deleteAllById(ids))
                                // 3. de las candidatas, ver cuáles siguen referenciadas
                                .then(referencedAmong(candidates))
                                .map(stillReferenced -> candidates.stream()
                                        .filter(techId -> !stillReferenced.contains(techId))
                                        .toList()));

        return pipeline.as(transactionalOperator::transactional)
                .flatMapMany(Flux::fromIterable);
    }

    private Mono<java.util.List<Long>> collectCandidateTechnologyIds(java.util.Collection<Long> capabilityIds) {
        return Flux.fromIterable(capabilityIds)
                .concatMap(capabilityTechnologyRepository::findByCapabilityId)
                .map(CapabilityTechnologyEntity::getTechnologyId)
                .distinct()
                .collectList();
    }

    private Mono<java.util.Set<Long>> referencedAmong(java.util.List<Long> candidateTechnologyIds) {
        if (candidateTechnologyIds.isEmpty()) {
            return Mono.just(java.util.Set.of());
        }
        return capabilityTechnologyRepository.findByTechnologyIdIn(candidateTechnologyIds)
                .map(CapabilityTechnologyEntity::getTechnologyId)
                .collect(java.util.stream.Collectors.toSet());
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
