package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

/**
 * DTO de solicitud para el registro de una capacidad en la capa driving (WebFlux).
 *
 * <p>Record inmutable que transporta los datos crudos recibidos en el cuerpo de la
 * petición {@code POST /api/v1/capabilities}. La normalización (trim) y las
 * validaciones de negocio se realizan en el dominio, no aquí.
 *
 * @param name          nombre propuesto para la capacidad.
 * @param description   descripción propuesta para la capacidad.
 * @param technologyIds identificadores de las tecnologías asociadas.
 */
public record CapabilityRequest(String name, String description, List<Long> technologyIds) {
}
