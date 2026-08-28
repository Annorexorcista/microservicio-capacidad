package com.bootcamp.capability.domain.spi;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Puerto de salida (spi) del dominio para la persistencia de capacidades.
 *
 * <p>Abstracción implementada por el adaptador driven de R2DBC. El dominio
 * depende de este contrato y no de detalles de persistencia (MySQL, R2DBC,
 * transacciones), manteniendo así la inversión de dependencias hexagonal.
 */
public interface ICapabilityPersistencePort {

    /**
     * Indica si ya existe una capacidad cuyo nombre coincide con el nombre
     * normalizado proporcionado, comparando sin distinción entre mayúsculas y
     * minúsculas.
     *
     * @param normalizedName nombre normalizado (trim) a comprobar.
     * @return un {@link Mono} que emite {@code true} si el nombre ya existe,
     *         {@code false} en caso contrario.
     */
    Mono<Boolean> existsByNameIgnoreCase(String normalizedName);

    /**
     * Persiste la capacidad y sus asociaciones con las tecnologías de forma
     * atómica (transaccional) y no bloqueante.
     *
     * @param capability capacidad de dominio a persistir ({@code id == null}).
     * @return un {@link Mono} que emite la capacidad persistida con su
     *         identificador asignado.
     */
    Mono<Capability> save(Capability capability);

    /**
     * Recupera la página de capacidades ya ordenada y paginada en la base de
     * datos según los parámetros de consulta (LIMIT/OFFSET y ORDER BY resueltos
     * en SQL). Cada {@link Capability} emitida incluye sus {@code technologyIds}
     * ya resueltos; el orden de emisión es el orden de la consulta.
     *
     * @param query parámetros de consulta (page, size, sortBy, direction).
     * @return un {@link Flux} con las capacidades de la página solicitada.
     */
    Flux<Capability> findPage(CapabilityPageQuery query);

    /**
     * Cuenta el total de capacidades existentes, para calcular la metadata de
     * paginación (totalElements, totalPages).
     *
     * @return un {@link Mono} que emite el total de capacidades.
     */
    Mono<Long> countAll();

    /**
     * Recupera las capacidades cuyos identificadores están en la colección dada.
     * Cada {@link Capability} emitida incluye sus {@code technologyIds} resueltos.
     * Emite solo las que existen (0..N; subconjunto de las solicitadas).
     *
     * @param ids identificadores de capacidad a recuperar.
     * @return un {@link Flux} con las capacidades existentes.
     */
    Flux<Capability> findByIds(Collection<Long> ids);
}
