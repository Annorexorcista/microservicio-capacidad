package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

/**
 * DTO de respuesta para una capacidad ya persistida en la capa driving (WebFlux).
 *
 * <p>Record inmutable que representa la capacidad creada, incluyendo su
 * identificador generado y el listado de identificadores de tecnología asociados.
 *
 * @param id            identificador de la capacidad asignado por la base de datos.
 * @param name          nombre normalizado de la capacidad.
 * @param description   descripción normalizada de la capacidad.
 * @param technologyIds identificadores de las tecnologías asociadas.
 */
public record CapabilityResponse(Long id, String name, String description, List<Long> technologyIds) {
}
