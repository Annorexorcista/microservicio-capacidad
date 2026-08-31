package com.bootcamp.capability.domain.model;

import java.util.List;

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
