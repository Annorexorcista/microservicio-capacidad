package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

/**
 * DTO de respuesta de una tecnología en el listado de capacidades, con
 * únicamente su identificador y su nombre.
 *
 * @param id   identificador de la tecnología.
 * @param name nombre de la tecnología.
 */
public record TechnologySummaryResponse(Long id, String name) {
}
