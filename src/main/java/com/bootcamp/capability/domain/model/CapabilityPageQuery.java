package com.bootcamp.capability.domain.model;

/**
 * Parámetros de consulta ya tipados para el listado paginado y ordenado de
 * capacidades.
 *
 * <p>Modelo de dominio puro e inmutable, sin anotaciones de framework. La capa
 * driving construye este objeto a partir de los query params (aplicando los
 * defaults y traduciendo {@code sortBy}/{@code sortDirection} a los enums de
 * dominio contra una lista blanca); el caso de uso valida el rango de
 * {@code page}/{@code size}.
 *
 * @see CapabilitySortBy
 * @see CapabilitySortDirection
 */
public final class CapabilityPageQuery {

    private final int page;
    private final int size;
    private final CapabilitySortBy sortBy;
    private final CapabilitySortDirection direction;

    public CapabilityPageQuery(int page,
                               int size,
                               CapabilitySortBy sortBy,
                               CapabilitySortDirection direction) {
        this.page = page;
        this.size = size;
        this.sortBy = sortBy;
        this.direction = direction;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public CapabilitySortBy getSortBy() {
        return sortBy;
    }

    public CapabilitySortDirection getDirection() {
        return direction;
    }
}
