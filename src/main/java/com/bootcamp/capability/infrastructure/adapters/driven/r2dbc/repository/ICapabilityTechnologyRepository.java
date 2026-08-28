package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository;

import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityTechnologyEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Repositorio reactivo R2DBC para la tabla puente {@link CapabilityTechnologyEntity}.
 *
 * <p>La entidad no tiene un {@code @Id} de una sola columna (la clave primaria de
 * la tabla es compuesta), por lo que este repositorio se tipa con {@link Long}
 * como identificador nominal pero NO se usa para {@code save}/{@code findById}
 * basados en id. La inserción de las filas del join la realiza el adaptador de
 * persistencia con {@code R2dbcEntityTemplate#insert}. Aquí se expone únicamente
 * una derived query de solo lectura para recuperar las asociaciones de una
 * capacidad.
 */
public interface ICapabilityTechnologyRepository
        extends ReactiveCrudRepository<CapabilityTechnologyEntity, Long> {

    /**
     * Recupera todas las asociaciones (filas del join) de una capacidad concreta.
     *
     * @param capabilityId identificador de la capacidad.
     * @return un {@link Flux} con las asociaciones encontradas.
     */
    Flux<CapabilityTechnologyEntity> findByCapabilityId(Long capabilityId);

    /**
     * Recupera las asociaciones cuyas tecnologías están en la colección dada.
     * Se usa para detectar qué tecnologías siguen referenciadas por alguna
     * capacidad tras un borrado (las que no aparezcan quedaron huérfanas).
     *
     * @param technologyIds identificadores de tecnología a comprobar.
     * @return un {@link Flux} con las asociaciones que aún referencian esas tecnologías.
     */
    Flux<CapabilityTechnologyEntity> findByTechnologyIdIn(Collection<Long> technologyIds);

    /**
     * Elimina todas las asociaciones de las capacidades indicadas.
     *
     * @param capabilityIds identificadores de capacidad cuyas asociaciones se borran.
     * @return un {@link Mono} que completa cuando el borrado ha terminado.
     */
    Mono<Void> deleteByCapabilityIdIn(Collection<Long> capabilityIds);
}
