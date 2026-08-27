package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.adapter;

import com.bootcamp.capability.domain.exception.CapabilityAlreadyExistsException;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
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
}
