package com.bootcamp.capability.domain.api;

import com.bootcamp.capability.domain.model.Capability;
import reactor.core.publisher.Mono;

/**
 * Puerto de entrada (api) del dominio para el registro de capacidades.
 *
 * <p>Define el contrato que la capa driving (WebFlux) consume para orquestar el
 * caso de uso de registro. Es una abstracción pura: no conoce detalles de HTTP,
 * Spring ni persistencia. El caso de uso {@code CapabilityUseCase} lo implementa.
 */
public interface ICapabilityServicePort {

    /**
     * Registra una capacidad aplicando las reglas de negocio (normalización,
     * validaciones de obligatoriedad/longitud, cantidad y no repetición de
     * tecnologías, unicidad del nombre y existencia de las tecnologías asociadas).
     *
     * @param capability capacidad de dominio a registrar ({@code id == null}).
     * @return un {@link Mono} que emite la capacidad persistida con su identificador
     *         asignado, o un error de dominio si alguna validación falla.
     */
    Mono<Capability> registerCapability(Capability capability);
}
