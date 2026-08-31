package com.bootcamp.capability.domain.model;

import java.util.List;

public final class CapabilityListItem {

    private final Long id;
    private final String name;
    private final String description;
    private final List<TechnologySummary> technologies;

    public CapabilityListItem(Long id,
                              String name,
                              String description,
                              List<TechnologySummary> technologies) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.technologies = technologies == null ? List.of() : List.copyOf(technologies);
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

    public List<TechnologySummary> getTechnologies() {
        return technologies;
    }
}
