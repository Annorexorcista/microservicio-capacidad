package com.bootcamp.capability.domain.model;

/**
 * Dirección del ordenamiento del listado de capacidades.
 *
 * <p>Enum puro del dominio, sin anotaciones de framework. La traducción desde
 * los valores de la API ({@code asc}, {@code desc}) hacia estos valores ocurre
 * en la capa driving, y la traducción hacia fragmentos SQL de lista blanca
 * ocurre en el adaptador de persistencia.
 *
 * <ul>
 *   <li>{@link #ASC}: ordenamiento ascendente.</li>
 *   <li>{@link #DESC}: ordenamiento descendente.</li>
 * </ul>
 */
public enum CapabilitySortDirection {
    ASC,
    DESC
}
