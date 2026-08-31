package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

public record CapabilityListItemResponse(
        Long id,
        String name,
        String description,
        List<TechnologySummaryResponse> technologies) {
}
