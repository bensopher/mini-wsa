package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.common.error.PayloadTooLargeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestBodyLimitFilterTest {

    private final List<Exception> resolved = new ArrayList<>();
    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(10, (request, response, handler, ex) -> {
        resolved.add(ex);
        return new ModelAndView();
    });

    @Test
    void declaredLengthOverTheLimitIsRejectedWithoutCallingTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/events/ingest");
        request.setContent(new byte[11]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(resolved).singleElement().isInstanceOf(PayloadTooLargeException.class)
                .extracting(Exception::getMessage).isEqualTo("The request body must not exceed 10 bytes");
    }

    @Test
    void bodyWithinTheLimitIsReadNormally() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/events/ingest");
        request.setContent("0123456789".getBytes());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize(10);
        assertThat(resolved).isEmpty();
    }

    @Test
    void bodyWithoutDeclaredLengthIsCutOffOnceItCrossesTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/events/ingest") {
            @Override
            public long getContentLengthLong() {
                return -1; // chunked: no Content-Length header
            }
        };
        request.setContent(new byte[11]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThatThrownBy(() -> chain.getRequest().getInputStream().readAllBytes())
                .isInstanceOf(PayloadTooLargeException.class);
    }
}
