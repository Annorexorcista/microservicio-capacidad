package com.bootcamp.capability.infrastructure.adapters.driving.webflux.mapper;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityRequest;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper puro (sin I/O ni tipos reactivos) que convierte entre los DTOs de la
 * capa driving (WebFlux) y el modelo de dominio {@link Capability}.
 *
 * <p>Las conversiones son transformaciones en memoria; se invocan dentro del
 * pipeline reactivo del handler (por ejemplo con {@code map}), por lo que este
 * componente no conoce Project Reactor ni detalles de HTTP. El modelo de dominio
 * permanece libre de anotaciones de framework.
 */
@Component
public class CapabilityDtoMapper {

    /**
     * Convierte un DTO de solicitud en modelo de dominio.
     *
     * <p>El {@code id} se fija en {@code null} porque la capacidad aún no ha sido
     * persistida; la base de datos asignará el identificador durante el INSERT. La
     * normalización (trim) y las validaciones se realizan en el dominio.
     *
     * @param request DTO recibido en la solicitud; puede ser {@code null}.
     * @return el modelo de dominio equivalente con {@code id} nulo, o {@code null}
     *         si {@code request} es {@code null}.
     */
    public Capability toDomain(CapabilityRequest request) {
        if (request == null) {
            return null;
        }
        return new Capability(null, request.name(), request.description(), request.technologyIds());
    }

    /**
     * Convierte un modelo de dominio ya persistido en DTO de respuesta.
     *
     * @param capability modelo de dominio a convertir; puede ser {@code null}.
     * @return el DTO de respuesta equivalente, o {@code null} si {@code capability}
     *         es {@code null}.
     */
    public CapabilityResponse toResponse(Capability capability) {
        if (capability == null) {
            return null;
        }
        return new CapabilityResponse(
                capability.getId(),
                capability.getName(),
                capability.getDescription(),
                capability.getTechnologyIds());
    }
}
