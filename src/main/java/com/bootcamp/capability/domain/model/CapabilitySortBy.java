package com.bootcamp.capability.domain.model;

/**
 * Criterio de ordenamiento del listado de capacidades.
 *
 * <p>Enum puro del dominio, sin anotaciones de framework. La traducción desde
 * los valores de la API ({@code name}, {@code technologyCount}) hacia estos
 * valores ocurre en la capa driving, y la traducción hacia fragmentos SQL de
 * lista blanca ocurre en el adaptador de persistencia.
 *
 * <ul>
 *   <li>{@link #NAME}: ordena por el nombre de la capacidad.</li>
 *   <li>{@link #TECHNOLOGY_COUNT}: ordena por la cantidad de tecnologías asociadas.</li>
 * </ul>
 */
public enum CapabilitySortBy {
    NAME,
    TECHNOLOGY_COUNT
}
