package io.labs64.paymentgateway.idempotency;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CachedBodyHttpServletRequestTest {

    @Test
    void cachedBodyCanBeReadMultipleTimes() throws Exception {
        final byte[] body = "{\"paymentId\":\"123\"}".getBytes(StandardCharsets.UTF_8);
        final CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(
                new MockHttpServletRequest("POST", "/api/v1/payments/123/pay"),
                body);

        assertThat(request.getCachedBody()).isEqualTo(body);
        assertThat(StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8))
                .isEqualTo("{\"paymentId\":\"123\"}");
        assertThat(StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8))
                .isEqualTo("{\"paymentId\":\"123\"}");
        assertThat(request.getReader().readLine()).isEqualTo("{\"paymentId\":\"123\"}");
    }

    @Test
    void getCachedBodyReturnsDefensiveCopy() {
        final byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        final CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(
                new MockHttpServletRequest("POST", "/api/v1/payments/123/pay"),
                body);

        request.getCachedBody()[0] = 'x';

        assertThat(request.getCachedBody()).isEqualTo(body);
    }
}
