package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository;

import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo R2DBC para la entidad {@link CapabilityEntity}.
 *
 * <p>Extiende {@link ReactiveCrudRepository} para heredar las operaciones CRUD
 * no bloqueantes (por ejemplo, {@code save} que retorna {@code Mono<CapabilityEntity>}
 * con el id autogenerado). La derived query {@code existsByNameIgnoreCase} genera
 * una consulta que compara el nombre sin distinción entre mayúsculas y minúsculas.
 */
public interface ICapabilityRepository extends ReactiveCrudRepository<CapabilityEntity, Long> {

    /**
     * Indica si existe una capacidad cuyo nombre coincide con el proporcionado,
     * ignorando mayúsculas y minúsculas.
     *
     * @param name nombre a comparar.
     * @return un {@link Mono} que emite {@code true} si existe, {@code false} en caso contrario.
     */
    Mono<Boolean> existsByNameIgnoreCase(String name);
}
