package com.bootcamp.capability.domain.model;

/**
 * Modelo de dominio reducido de una tecnología, compuesto únicamente por su
 * identificador y su nombre.
 *
 * <p>Clase inmutable y pura (sin anotaciones de framework). Se usa para enriquecer
 * cada capacidad del listado con sus tecnologías (id + name), obtenidas del
 * Technology_Service mediante una única llamada por lotes.
 */
public final class TechnologySummary {

    private final Long id;
    private final String name;

    public TechnologySummary(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
