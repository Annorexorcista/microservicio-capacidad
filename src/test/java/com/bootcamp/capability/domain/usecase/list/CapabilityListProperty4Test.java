package com.bootcamp.capability.domain.usecase.list;

import com.bootcamp.capability.domain.exception.InvalidPageQueryException;
import com.bootcamp.capability.domain.exception.PageErrorCode;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import com.bootcamp.capability.domain.usecase.CapabilityUseCase;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Feature: listar-capacidades, Property 4 — Validates: Requirements 4.3, 4.4, 4.5
 *
 * <p><b>Property 4: Parámetros de paginación fuera de rango se rechazan sin consultar.</b>
 * Para todo {@link CapabilityPageQuery} con {@code page < 0}, {@code size < 1} o
 * {@code size > 100}, {@code listCapabilities} emite {@link InvalidPageQueryException}
 * con el código correspondiente y no invoca {@code findPage}, {@code countAll} ni
 * el gateway.
 */
class CapabilityListProperty4Test {

    @Property(tries = 200)
    void negativePageIsRejectedWithoutQuerying(
            @ForAll @IntRange(min = 1, max = 100_000) int magnitude,
            @ForAll @IntRange(min = 1, max = 100) int size) {

        int page = -magnitude;
        assertRejected(new CapabilityPageQuery(page, size,
                CapabilitySortBy.NAME, CapabilitySortDirection.ASC), PageErrorCode.PAGE_NEGATIVE);
    }

    @Property(tries = 200)
    void tooSmallSizeIsRejectedWithoutQuerying(
            @ForAll @IntRange(min = 0, max = 10_000) int page,
            @ForAll @IntRange(min = 0, max = 100_000) int magnitude) {

        int size = -magnitude; // <= 0
        assertRejected(new CapabilityPageQuery(page, size,
                CapabilitySortBy.NAME, CapabilitySortDirection.ASC), PageErrorCode.SIZE_TOO_SMALL);
    }

    @Property(tries = 200)
    void tooLargeSizeIsRejectedWithoutQuerying(
            @ForAll @IntRange(min = 0, max = 10_000) int page,
            @ForAll @IntRange(min = 101, max = 1_000_000) int size) {

        assertRejected(new CapabilityPageQuery(page, size,
                CapabilitySortBy.NAME, CapabilitySortDirection.ASC), PageErrorCode.SIZE_TOO_LARGE);
    }

    private void assertRejected(CapabilityPageQuery query, PageErrorCode expectedCode) {
        ICapabilityPersistencePort persistencePort = mock(ICapabilityPersistencePort.class);
        ITechnologyGatewayPort gatewayPort = mock(ITechnologyGatewayPort.class);
        CapabilityUseCase useCase = new CapabilityUseCase(persistencePort, gatewayPort);

        Throwable error = null;
        try {
            useCase.listCapabilities(query).block();
        } catch (Throwable t) {
            error = t;
        }
        if (!(error instanceof InvalidPageQueryException ipe) || ipe.getCode() != expectedCode) {
            throw new AssertionError("esperado InvalidPageQueryException(" + expectedCode
                    + "), obtenido=" + error);
        }
        verify(persistencePort, never()).findPage(any());
        verify(persistencePort, never()).countAll();
        verify(gatewayPort, never()).findTechnologiesByIds(anyCollection());
    }
}
