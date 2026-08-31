package com.bootcamp.capability.domain.model;

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
