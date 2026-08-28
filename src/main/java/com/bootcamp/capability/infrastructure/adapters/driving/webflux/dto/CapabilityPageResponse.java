package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

/**
 * DTO de respuesta paginada del listado de capacidades, con la metadata de
 * paginación y el contenido de la página.
 *
 * @param page          número de página (base cero).
 * @param size          tamaño de página solicitado.
 * @param totalElements total de capacidades existentes.
 * @param totalPages    total de páginas para el tamaño solicitado.
 * @param content       capacidades de la página, enriquecidas con sus tecnologías.
 */
public record CapabilityPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<CapabilityListItemResponse> content) {
}
