package com.bootcamp.capability.domain.model;

import java.util.List;

/**
 * Modelo de dominio puro que representa una capacidad.
 *
 * <p>Clase inmutable sin anotaciones de framework: sus valores se fijan en el
 * constructor y solo se exponen mediante getters (no hay setters). El mapeo a
 * persistencia (R2DBC) y a transporte (DTOs) ocurre en los adaptadores, por lo
 * que este modelo permanece libre de acoplamiento a Spring, R2DBC o Jackson.
 *
 * <p>Una capacidad agrupa tecnologías del bootcamp; persiste únicamente los
 * identificadores de tecnología ({@code technologyIds}), ya que el catálogo de
 * tecnologías es propiedad del microservicio de Tecnología.
 */
public final class Capability {

    private final Long id;
    private final String name;
    private final String description;
    private final List<Long> technologyIds;

    public Capability(Long id, String name, String description, List<Long> technologyIds) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.technologyIds = technologyIds;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Long> getTechnologyIds() {
        return technologyIds;
    }
}
