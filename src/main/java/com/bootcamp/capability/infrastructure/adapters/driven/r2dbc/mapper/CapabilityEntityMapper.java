package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.mapper;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper puro (sin I/O ni tipos reactivos) que convierte entre el modelo de
 * dominio {@link Capability} y la entidad de persistencia {@link CapabilityEntity}.
 *
 * <p>Las conversiones son transformaciones en memoria; se invocan dentro del
 * pipeline reactivo del adaptador, por lo que este componente no conoce Project
 * Reactor ni R2DBC. La asociación N:M con las tecnologías no se representa en
 * {@link CapabilityEntity} (vive en la tabla puente), por lo que
 * {@link #toDomain(CapabilityEntity, List)} recibe los identificadores de
 * tecnología por separado.
 */
@Component
public class CapabilityEntityMapper {

    /**
     * Convierte un modelo de dominio en entidad de persistencia.
     *
     * <p>Cuando el {@code id} del dominio es {@code null}, el {@code id} de la
     * entidad también queda en {@code null}, de modo que Spring Data R2DBC trate
     * la fila como nueva ({@code isNew}) y ejecute un INSERT.
     *
     * @param capability modelo de dominio a convertir; puede ser {@code null}
     * @return la entidad equivalente, o {@code null} si {@code capability} es {@code null}
     */
    public CapabilityEntity toEntity(Capability capability) {
        if (capability == null) {
            return null;
        }
        return new CapabilityEntity(
                capability.getId(),
                capability.getName(),
                capability.getDescription());
    }

    /**
     * Convierte una entidad de persistencia y su lista de identificadores de
     * tecnología asociados en un modelo de dominio.
     *
     * @param entity        entidad a convertir; puede ser {@code null}
     * @param technologyIds identificadores de tecnología asociados a la capacidad
     * @return el modelo de dominio equivalente, o {@code null} si {@code entity} es {@code null}
     */
    public Capability toDomain(CapabilityEntity entity, List<Long> technologyIds) {
        if (entity == null) {
            return null;
        }
        return new Capability(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                technologyIds);
    }
}
