package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

public record CapabilityPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<CapabilityListItemResponse> content) {
}
