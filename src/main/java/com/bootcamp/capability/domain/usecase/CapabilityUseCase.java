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

    @Override
    public Flux<CapabilityListItem> findCapabilitiesByIds(Collection<Long> ids) {
        return persistencePort.findByIds(ids)
                .collectList()
                .flatMapMany(capabilities -> capabilities.isEmpty()
                        ? Flux.empty()
                        : enrichWithTechnologies(capabilities).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<Void> deleteCapabilitiesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.empty();
        }
        return persistencePort.deleteByIdsReturningOrphanTechnologyIds(ids)
                .collectList()
                .flatMap(orphanTechnologyIds -> orphanTechnologyIds.isEmpty()
                        ? Mono.empty()
                        : technologyGatewayPort.deleteTechnologiesByIds(orphanTechnologyIds));
    }

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

    private Mono<Capability> ensureNameIsUnique(Capability capability) {
        return persistencePort.existsByNameIgnoreCase(capability.getName())
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(new CapabilityAlreadyExistsException(capability.getName()))
                        : Mono.just(capability));
    }

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
