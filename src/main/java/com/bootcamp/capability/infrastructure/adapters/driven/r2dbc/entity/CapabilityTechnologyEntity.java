package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad de persistencia R2DBC mapeada a la tabla puente {@code capability_technology}.
 *
 * <p>Modela una fila de la relación N:M entre una capacidad y una tecnología. La
 * tabla tiene una clave primaria compuesta {@code (capability_id, technology_id)};
 * sin embargo, Spring Data R2DBC no soporta entidades con clave compuesta ni un
 * campo {@code @Id} multi-columna, por lo que esta entidad se modela como un
 * mapeo simple de las filas del join (sin {@code @Id}).
 *
 * <p>El adaptador de persistencia inserta estas filas explícitamente (una por
 * cada {@code technologyId} asociado a la capacidad) mediante
 * {@code R2dbcEntityTemplate#insert}, ya que sin {@code @Id} no puede inferir si
 * la fila es nueva para un {@code save} basado en CRUD.
 */
@Table("capability_technology")
public class CapabilityTechnologyEntity {

    @Column("capability_id")
    private Long capabilityId;

    @Column("technology_id")
    private Long technologyId;

    public CapabilityTechnologyEntity() {
    }

    public CapabilityTechnologyEntity(Long capabilityId, Long technologyId) {
        this.capabilityId = capabilityId;
        this.technologyId = technologyId;
    }

    public Long getCapabilityId() {
        return capabilityId;
    }

    public void setCapabilityId(Long capabilityId) {
        this.capabilityId = capabilityId;
    }

    public Long getTechnologyId() {
        return technologyId;
    }

    public void setTechnologyId(Long technologyId) {
        this.technologyId = technologyId;
    }
}
