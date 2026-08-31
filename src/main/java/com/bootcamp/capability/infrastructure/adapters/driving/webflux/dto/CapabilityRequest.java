package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.util.List;

public record CapabilityRequest(String name, String description, List<Long> technologyIds) {
}
