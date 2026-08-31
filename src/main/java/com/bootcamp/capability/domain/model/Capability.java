package com.bootcamp.capability.domain.model;

import java.util.List;

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
