package io.labs64.paymentgateway.idempotency;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyRequestBodyCachingFilterTest {

    private final IdempotencyRequestBodyCachingFilter filter = new IdempotencyRequestBodyCachingFilter();

    @Test
    void skipsWrappingWhenIdempotencyKeyIsMissing() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/123/pay");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.request).isSameAs(request);
    }

    @Test
    void wrapsRequestWhenIdempotencyKeyIsPresent() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/123/pay");
        request.addHeader(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "idk-1");
        request.setContent("{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
        final CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.request).isInstanceOf(CachedBodyHttpServletRequest.class);
        assertThat(((CachedBodyHttpServletRequest) chain.request).getCachedBody())
                .isEqualTo("{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
    }

    private static class CapturingFilterChain extends MockFilterChain {
        private ServletRequest request;

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) {
            this.request = request;
        }
    }
}
