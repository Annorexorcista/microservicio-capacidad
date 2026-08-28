package com.bootcamp.capability.domain.api;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.PagedResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

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

    /**
     * Lista las capacidades de forma paginada y ordenada según los parámetros de
     * consulta, enriqueciendo cada capacidad con sus tecnologías (id y nombre)
     * mediante una única llamada por lotes al Technology_Service.
     *
     * @param query parámetros de consulta ya tipados (page, size, sortBy, direction).
     * @return un {@link Mono} que emite el {@link PagedResult} con la metadata de
     *         paginación y el contenido de la página, o un error de dominio si los
     *         parámetros son inválidos o el Technology_Service no está disponible.
     */
    Mono<PagedResult<CapabilityListItem>> listCapabilities(CapabilityPageQuery query);

    /**
     * Recupera las capacidades correspondientes a los identificadores dados,
     * cada una enriquecida con sus tecnologías (id y nombre) mediante una única
     * llamada por lotes al Technology_Service. Pensado para el consumo entre
     * microservicios (por ejemplo, el microservicio de Bootcamp), que necesita
     * los datos completos de un conjunto concreto de capacidades.
     *
     * @param ids identificadores de capacidad a recuperar.
     * @return un {@link Flux} de {@link CapabilityListItem} de las capacidades
     *         existentes; vacío si {@code ids} es vacío.
     */
    Flux<CapabilityListItem> findCapabilitiesByIds(Collection<Long> ids);
}
