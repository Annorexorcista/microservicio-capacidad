package com.bootcamp.capability.infrastructure.adapters.driving.webflux.mapper;

import com.bootcamp.capability.domain.exception.InvalidPageQueryException;
import com.bootcamp.capability.domain.exception.PageErrorCode;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.model.PagedResult;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityListItemResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityPageResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityRequest;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.TechnologySummaryResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.exception.InvalidIdsQueryException;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.exception.RequestErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Arrays;
import java.util.List;

@Component
public class CapabilityDtoMapper {

    public Capability toDomain(CapabilityRequest request) {
        if (request == null) {
            return null;
        }
        return new Capability(null, request.name(), request.description(), request.technologyIds());
    }

    public CapabilityResponse toResponse(Capability capability) {
        if (capability == null) {
            return null;
        }
        return new CapabilityResponse(
                capability.getId(),
                capability.getName(),
                capability.getDescription(),
                capability.getTechnologyIds());
    }

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;

    public CapabilityPageQuery toPageQuery(ServerRequest request) {
        int page = parseIntParam(request, "page", DEFAULT_PAGE);
        int size = parseIntParam(request, "size", DEFAULT_SIZE);
        CapabilitySortBy sortBy = parseSortBy(request.queryParam("sortBy").orElse(null));
        CapabilitySortDirection direction =
                parseSortDirection(request.queryParam("sortDirection").orElse(null));
        return new CapabilityPageQuery(page, size, sortBy, direction);
    }

    private int parseIntParam(ServerRequest request, String name, int defaultValue) {
        String raw = request.queryParam(name).orElse(null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            PageErrorCode code = "size".equals(name)
                    ? PageErrorCode.SIZE_TOO_SMALL
                    : PageErrorCode.PAGE_NEGATIVE;
            throw new InvalidPageQueryException(code);
        }
    }

    private CapabilitySortBy parseSortBy(String raw) {
        if (raw == null || raw.isBlank()) {
            return CapabilitySortBy.NAME;
        }
        return switch (raw.trim()) {
            case "name" -> CapabilitySortBy.NAME;
            case "technologyCount" -> CapabilitySortBy.TECHNOLOGY_COUNT;
            default -> throw new InvalidPageQueryException(PageErrorCode.SORT_BY_INVALID);
        };
    }

    private CapabilitySortDirection parseSortDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return CapabilitySortDirection.ASC;
        }
        return switch (raw.trim().toLowerCase()) {
            case "asc" -> CapabilitySortDirection.ASC;
            case "desc" -> CapabilitySortDirection.DESC;
            default -> throw new InvalidPageQueryException(PageErrorCode.SORT_DIRECTION_INVALID);
        };
    }

    public CapabilityPageResponse toPageResponse(PagedResult<CapabilityListItem> pagedResult) {
        if (pagedResult == null) {
            return null;
        }
        List<CapabilityListItemResponse> content = pagedResult.getContent().stream()
                .map(this::toListItemResponse)
                .toList();
        return new CapabilityPageResponse(
                pagedResult.getPage(),
                pagedResult.getSize(),
                pagedResult.getTotalElements(),
                pagedResult.getTotalPages(),
                content);
    }

    public List<Long> parseIds(ServerRequest request) {
        return request.queryParam("ids")
                .map(this::parseIds)
                .orElseThrow(() -> new InvalidIdsQueryException(RequestErrorCode.IDS_REQUIRED));
    }

    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidIdsQueryException(RequestErrorCode.IDS_REQUIRED);
        }

        return Arrays.stream(raw.split(",", -1))
                .map(String::trim)
                .map(this::parsePositiveId)
                .distinct()
                .toList();
    }

    private Long parsePositiveId(String segment) {
        if (segment.isEmpty()) {
            throw new InvalidIdsQueryException(RequestErrorCode.ID_EMPTY);
        }
        if (!segment.matches("[0-9]+")) {
            throw new InvalidIdsQueryException(RequestErrorCode.ID_NOT_NUMERIC, segment);
        }

        try {
            long id = Long.parseLong(segment);
            if (id <= 0) {
                throw new InvalidIdsQueryException(RequestErrorCode.ID_NOT_POSITIVE, segment);
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new InvalidIdsQueryException(RequestErrorCode.ID_OUT_OF_RANGE, exception, segment);
        }
    }

    public CapabilityListItemResponse toListItemResponse(CapabilityListItem item) {
        List<TechnologySummaryResponse> technologies = item.getTechnologies().stream()
                .map(t -> new TechnologySummaryResponse(t.getId(), t.getName()))
                .toList();
        return new CapabilityListItemResponse(
                item.getId(), item.getName(), item.getDescription(), technologies);
    }
}
