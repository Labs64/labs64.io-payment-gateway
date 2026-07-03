package io.labs64.paymentgateway.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for browser checkout continuation callbacks from PSP providers.
 */
public interface ProviderCheckoutService {

    /**
     * Completes a browser checkout session after customer return from the PSP.
     *
     * @param provider provider identifier from the callback path
     * @param sessionId checkout session identifier
     * @param queryParams callback query parameters
     * @return redirect target for the browser
     */
    URI complete(String provider, UUID sessionId, Map<String, List<String>> queryParams);

    /**
     * Cancels a browser checkout session after customer cancellation at the PSP.
     *
     * @param provider provider identifier from the callback path
     * @param sessionId checkout session identifier
     * @param queryParams callback query parameters
     * @return redirect target for the browser
     */
    URI cancel(String provider, UUID sessionId, Map<String, List<String>> queryParams);
}
