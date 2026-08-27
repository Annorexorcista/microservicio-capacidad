package com.bootcamp.capability.domain.spi;

import com.bootcamp.capability.domain.model.Capability;
import reactor.core.publisher.Mono;

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
}
