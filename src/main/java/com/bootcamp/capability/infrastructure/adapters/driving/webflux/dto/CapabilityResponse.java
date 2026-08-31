package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

public record CapabilityResponse(Long id, String name, String description, List<Long> technologyIds) {
}
