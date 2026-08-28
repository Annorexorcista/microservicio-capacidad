package com.bootcamp.capability.domain.usecase.list;

import com.bootcamp.capability.domain.model.CapabilityListItem;
import com.bootcamp.capability.domain.model.PagedResult;
import com.bootcamp.capability.domain.model.TechnologySummary;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityListItemResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityPageResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.TechnologySummaryResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.mapper.CapabilityDtoMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.util.List;

/**
 * Feature: listar-capacidades, Property 7 — Validates: Requirements 5.4
 *
 * <p><b>Property 7: La respuesta conserva la estructura de cada capacidad y sus tecnologías.</b>
 * Para todo {@link PagedResult}, el {@link CapabilityPageResponse} mapeado conserva
 * page, size, totalElements, totalPages, la cantidad y el orden de los items, y para
 * cada item su id, name, description y una lista de tecnologías donde cada elemento
 * contiene únicamente id y name.
 */
class CapabilityListProperty7Test {

    private final CapabilityDtoMapper mapper = new CapabilityDtoMapper();

    @Property(tries = 200)
    void pageResponseKeepsStructure(
            @ForAll @IntRange(min = 0, max = 10_000) int page,
            @ForAll @IntRange(min = 1, max = 100) int size,
            @ForAll @LongRange(min = 0, max = 1_000_000) long totalElements,
            @ForAll("items") List<CapabilityListItem> items) {

        PagedResult<CapabilityListItem> pagedResult =
                new PagedResult<>(page, size, totalElements, items);

        CapabilityPageResponse response = mapper.toPageResponse(pagedResult);

        if (response.page() != page || response.size() != size
                || response.totalElements() != totalElements
                || response.totalPages() != pagedResult.getTotalPages()) {
            throw new AssertionError("metadata no conservada");
        }
        if (response.content().size() != items.size()) {
            throw new AssertionError("cantidad de items alterada");
        }
        for (int i = 0; i < items.size(); i++) {
            CapabilityListItem src = items.get(i);
            CapabilityListItemResponse dst = response.content().get(i);
            if (!java.util.Objects.equals(src.getId(), dst.id())
                    || !java.util.Objects.equals(src.getName(), dst.name())
                    || !java.util.Objects.equals(src.getDescription(), dst.description())) {
                throw new AssertionError("campos del item alterados en la posición " + i);
            }
            if (src.getTechnologies().size() != dst.technologies().size()) {
                throw new AssertionError("cantidad de tecnologías alterada");
            }
            for (int j = 0; j < src.getTechnologies().size(); j++) {
                TechnologySummary st = src.getTechnologies().get(j);
                TechnologySummaryResponse dt = dst.technologies().get(j);
                if (!java.util.Objects.equals(st.getId(), dt.id())
                        || !java.util.Objects.equals(st.getName(), dt.name())) {
                    throw new AssertionError("tecnología alterada");
                }
            }
        }
    }

    @Provide
    Arbitrary<List<CapabilityListItem>> items() {
        Arbitrary<TechnologySummary> tech = net.jqwik.api.Combinators.combine(
                        Arbitraries.longs().between(1L, 1000L),
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10))
                .as(TechnologySummary::new);
        Arbitrary<List<TechnologySummary>> techList = tech.list().ofMinSize(0).ofMaxSize(5);
        Arbitrary<CapabilityListItem> item = net.jqwik.api.Combinators.combine(
                        Arbitraries.longs().between(1L, 100_000L),
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30),
                        techList)
                .as(CapabilityListItem::new);
        return item.list().ofMinSize(0).ofMaxSize(10);
    }
}
