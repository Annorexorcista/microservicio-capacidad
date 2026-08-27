package com.bootcamp.capability.infrastructure.adapters.driven.http.dto;

/**
 * DTO de respuesta del Technology_Service para la consulta
 * {@code GET /api/v1/technologies?ids=1,2,3}.
 *
 * <p>El endpoint devuelve un arreglo JSON con las tecnologías existentes en el
 * formato {@code [{id, name, description}]}. Solo el {@code id} es necesario para
 * validar existencia; {@code name} y {@code description} se incluyen para reflejar
 * fielmente el contrato del servicio consumido.
 *
 * @param id          identificador de la tecnología existente.
 * @param name        nombre de la tecnología.
 * @param description descripción de la tecnología.
 */
public record TechnologyGatewayResponse(Long id, String name, String description) {
}
