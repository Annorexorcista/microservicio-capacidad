package com.bootcamp.capability.infrastructure.adapters.driving.webflux.mapper;

import com.bootcamp.capability.infrastructure.adapters.driving.webflux.exception.InvalidIdsQueryException;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.exception.RequestErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityDtoMapperIdsTest {

    private final CapabilityDtoMapper mapper = new CapabilityDtoMapper();

    @Test
    void parseIds_withValidCsv_returnsDistinctIdsInOrder() {
        ServerRequest request = requestWithIds(" 3,1,3,2 ");

        assertThat(mapper.parseIds(request)).containsExactly(3L, 1L, 2L);
    }

    @Test
    void parseIds_withoutIds_throwsRequiredError() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("ids")).thenReturn(Optional.empty());

        assertInvalidIdsError(request, RequestErrorCode.IDS_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "1,abc,3",
            "1,-2,3",
            "1,0,3",
            "1,,3",
            "1,2,",
            "9223372036854775808"
    })
    void parseIds_withInvalidCsv_throwsRequestError(String rawIds) {
        assertThatThrownBy(() -> mapper.parseIds(requestWithIds(rawIds)))
                .isInstanceOf(InvalidIdsQueryException.class);
    }

    private ServerRequest requestWithIds(String rawIds) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("ids")).thenReturn(Optional.of(rawIds));
        return request;
    }

    private void assertInvalidIdsError(ServerRequest request, RequestErrorCode expectedCode) {
        assertThatThrownBy(() -> mapper.parseIds(request))
                .isInstanceOfSatisfying(InvalidIdsQueryException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode));
    }
}
