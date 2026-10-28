package com.loyiso.booking.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void generatesAnIdWhenNoneIsSupplied() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
    }

    @Test
    void echoesASafeSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "trace-me_42.a");

        assertThat(run(request).getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-me_42.a");
    }

    @Test
    void replacesAnUnsafeSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "bad id\nforged log line");

        String id = run(request).getHeader(RequestIdFilter.HEADER);

        assertThat(id).isNotBlank().doesNotContain("\n").doesNotContain(" ");
    }

    @Test
    void exposesTheIdToLoggingDuringTheRequestAndClearsItAfterwards() throws Exception {
        AtomicReference<String> seenDuringRequest = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response,
                (req, res) -> seenDuringRequest.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seenDuringRequest.get()).isEqualTo(response.getHeader(RequestIdFilter.HEADER));
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}