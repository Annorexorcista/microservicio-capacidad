package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.mapper;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CapabilityEntityMapper {

    public CapabilityEntity toEntity(Capability capability) {
        if (capability == null) {
            return null;
        }
        return new CapabilityEntity(
                capability.getId(),
                capability.getName(),
                capability.getDescription());
    }

    public Capability toDomain(CapabilityEntity entity, List<Long> technologyIds) {
        if (entity == null) {
            return null;
        }
        return new Capability(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                technologyIds);
    }
}
