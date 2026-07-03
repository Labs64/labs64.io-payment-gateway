package io.labs64.paymentgateway.web;

import java.io.IOException;
import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static io.labs64.paymentgateway.correlation.CorrelationConstants.CORRELATION_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        CorrelationContextHolder.clear();
    }

    @Test
    void usesIncomingCorrelationIdAndClearsContextAfterRequest() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");
        request.addHeader(CORRELATION_ID_HEADER, "incoming-correlation");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new AssertingFilterChain("incoming-correlation"));

        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo("incoming-correlation");
        assertThat(CorrelationContextHolder.get()).isEmpty();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CORRELATION_ID_HEADER))
                .isNotBlank()
                .satisfies(correlationId -> assertThat(UUID.fromString(correlationId)).isNotNull());
        assertThat(CorrelationContextHolder.get()).isEmpty();
    }

    private static class AssertingFilterChain extends MockFilterChain {
        private final String expectedCorrelationId;

        AssertingFilterChain(final String expectedCorrelationId) {
            this.expectedCorrelationId = expectedCorrelationId;
        }

        @Override
        public void doFilter(final jakarta.servlet.ServletRequest request, final jakarta.servlet.ServletResponse response) {
            assertThat(CorrelationContextHolder.require()).isEqualTo(expectedCorrelationId);
        }
    }
}
