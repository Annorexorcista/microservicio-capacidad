package com.bootcamp.capability.domain.model;

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
