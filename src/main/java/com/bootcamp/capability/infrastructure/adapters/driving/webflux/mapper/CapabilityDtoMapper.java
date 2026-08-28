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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.List;

/**
 * Mapper puro (sin I/O ni tipos reactivos) que convierte entre los DTOs de la
 * capa driving (WebFlux) y el modelo de dominio {@link Capability}.
 *
 * <p>Las conversiones son transformaciones en memoria; se invocan dentro del
 * pipeline reactivo del handler (por ejemplo con {@code map}), por lo que este
 * componente no conoce Project Reactor ni detalles de HTTP. El modelo de dominio
 * permanece libre de anotaciones de framework.
 */
@Component
public class CapabilityDtoMapper {

    /**
     * Convierte un DTO de solicitud en modelo de dominio.
     *
     * <p>El {@code id} se fija en {@code null} porque la capacidad aún no ha sido
     * persistida; la base de datos asignará el identificador durante el INSERT. La
     * normalización (trim) y las validaciones se realizan en el dominio.
     *
     * @param request DTO recibido en la solicitud; puede ser {@code null}.
     * @return el modelo de dominio equivalente con {@code id} nulo, o {@code null}
     *         si {@code request} es {@code null}.
     */
    public Capability toDomain(CapabilityRequest request) {
        if (request == null) {
            return null;
        }
        return new Capability(null, request.name(), request.description(), request.technologyIds());
    }

    /**
     * Convierte un modelo de dominio ya persistido en DTO de respuesta.
     *
     * @param capability modelo de dominio a convertir; puede ser {@code null}.
     * @return el DTO de respuesta equivalente, o {@code null} si {@code capability}
     *         es {@code null}.
     */
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

    /**
     * Construye un {@link CapabilityPageQuery} de dominio a partir de los query
     * params de la solicitud, aplicando los defaults (page=0, size=10,
     * sortBy=name, sortDirection=asc) cuando faltan y traduciendo
     * {@code sortBy}/{@code sortDirection} a los enums de dominio contra una
     * lista blanca.
     *
     * <p>Un {@code page}/{@code size} no numérico o un {@code sortBy}/
     * {@code sortDirection} fuera de la lista blanca produce una
     * {@link InvalidPageQueryException} (traducida a 400 por el handler global).
     * El rango de {@code page}/{@code size} lo valida el caso de uso.
     *
     * @param request solicitud del servidor con los query params.
     * @return el query de dominio ya tipado.
     */
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

    /**
     * Convierte el {@link PagedResult} de dominio en el DTO de respuesta paginada,
     * conservando la metadata, el orden y la cantidad de items, y mapeando cada
     * tecnología a {@link TechnologySummaryResponse} (id + name).
     *
     * @param pagedResult resultado paginado del dominio; puede ser {@code null}.
     * @return el DTO de respuesta paginada, o {@code null} si {@code pagedResult}
     *         es {@code null}.
     */
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

    /**
     * Parsea el query param {@code ids} (CSV de enteros, p. ej. {@code 1,2,3}) a
     * una lista de identificadores. Devuelve lista vacía si el parámetro falta o
     * está en blanco. Un valor no numérico produce {@link NumberFormatException},
     * que el handler global traduce a 400.
     *
     * @param request solicitud del servidor.
     * @return la lista de ids solicitados (posiblemente vacía).
     */
    public List<Long> parseIds(ServerRequest request) {
        String raw = request.queryParam("ids").orElse(null);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    /**
     * Convierte un {@link CapabilityListItem} de dominio en su DTO de respuesta
     * (id, name, description y tecnologías con id + name).
     *
     * @param item item de dominio a convertir.
     * @return el DTO de respuesta equivalente.
     */
    public CapabilityListItemResponse toListItemResponse(CapabilityListItem item) {
        List<TechnologySummaryResponse> technologies = item.getTechnologies().stream()
                .map(t -> new TechnologySummaryResponse(t.getId(), t.getName()))
                .toList();
        return new CapabilityListItemResponse(
                item.getId(), item.getName(), item.getDescription(), technologies);
    }
}
