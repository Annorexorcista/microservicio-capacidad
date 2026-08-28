package com.bootcamp.capability.domain.model;

import java.util.List;

/**
 * Contenedor genérico e inmutable de una página de resultados.
 *
 * <p>Modelo de dominio puro, sin anotaciones de framework. Incluye la metadata
 * de paginación ({@code page}, {@code size}, {@code totalElements},
 * {@code totalPages}) y el contenido de la página ({@code content}).
 *
 * <p>El {@code totalPages} se deriva en el constructor como el techo de
 * {@code totalElements / size}, de modo que la metadata siempre es coherente,
 * incluso cuando el {@code content} es vacío por una página fuera de rango.
 *
 * @param <T> tipo de los elementos del contenido de la página.
 */
public final class PagedResult<T> {

    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final List<T> content;

    public PagedResult(int page, int size, long totalElements, List<T> content) {
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = size <= 0
                ? 0
                : (int) Math.ceil((double) totalElements / size);
        this.content = content == null ? List.of() : List.copyOf(content);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<T> getContent() {
        return content;
    }
}
