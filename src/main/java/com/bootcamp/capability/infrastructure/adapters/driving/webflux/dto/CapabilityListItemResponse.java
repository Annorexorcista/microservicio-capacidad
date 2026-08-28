package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

/**
 * DTO de respuesta de una capacidad dentro del listado paginado, incluyendo el
 * detalle de sus tecnologías (id y nombre).
 *
 * @param id           identificador de la capacidad.
 * @param name         nombre de la capacidad.
 * @param description  descripción de la capacidad.
 * @param technologies tecnologías asociadas, cada una con id y nombre.
 */
public record CapabilityListItemResponse(
        Long id,
        String name,
        String description,
        List<TechnologySummaryResponse> technologies) {
}
