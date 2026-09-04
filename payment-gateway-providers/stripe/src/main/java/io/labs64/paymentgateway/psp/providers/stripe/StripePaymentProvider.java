package io.labs64.paymentgateway.psp.providers.stripe;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.stripe.StripeClient;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.labs64.paymentgateway.model.BillingInfo;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.CheckoutSessionDraft;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.psp.spi.PaymentNextActionType;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookContext;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutContext;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutSupport;
import io.labs64.paymentgateway.psp.spi.ProviderConfigField;
import io.labs64.paymentgateway.psp.spi.ProviderConfigSupport;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionException;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure;
import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import io.labs64.paymentgateway.psp.spi.ProviderWebhookSupport;
import io.labs64.paymentgateway.psp.spi.StatusDetails;
import io.labs64.paymentgateway.psp.spi.WebhookRejectedException;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;

import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.AWAITING_CUSTOMER;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.CANCELLED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.COMPLETED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.EXPIRED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.PAYMENT_FAILED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.PROCESSING;
import static io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.AUTHENTICATION_FAILED;
import static io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.INVALID_RESPONSE;
import static io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE;

/**
 * Stripe-hosted Checkout provider.
 * <p>
 * The provider owns Stripe payloads, API calls, signature verification, and status mapping. It
 * receives gateway-owned callback URLs through the checkout SPI and never constructs gateway
 * routes itself.
 */
public class StripePaymentProvider implements PaymentProvider, ProviderConfigSupport,
        ProviderCheckoutSupport, ProviderWebhookSupport {

    public static final String PROVIDER = "stripe";

    static final String SECRET_KEY = "secretKey";
    static final String WEBHOOK_SECRET = "webhookSecret";
    static final String PAYMENT_TRANSACTION_ID = "paymentTransactionId";

    private static final String RETURN_URL = "returnUrl";
    private static final String CANCEL_URL = "cancelUrl";
    private static final String STRIPE_SESSION_ID = "stripeSessionId";
    private static final String STRIPE_SIGNATURE = "Stripe-Signature";
    private static final String CHECKOUT_SESSION_PLACEHOLDER = "{CHECKOUT_SESSION_ID}";
    private static final String PAID = "paid";
    private static final String NO_PAYMENT_REQUIRED = "no_payment_required";
    private static final int MAX_PRODUCT_NAME_LENGTH = 127;

    private static final Set<ProviderConfigField> CONFIG_FIELDS = Set.of(
            ProviderConfigField.required(SECRET_KEY),
            ProviderConfigField.required(WEBHOOK_SECRET));

    private final StripeClientFactory clientFactory;

    public StripePaymentProvider(final StripeClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Set<ProviderConfigField> configFields() {
        return CONFIG_FIELDS;
    }

    @Override
    public void validateConfig(final Map<String, String> config) {
        final String secretKey = config.get(SECRET_KEY);
        if (secretKey == null || (!secretKey.startsWith("sk_") && !secretKey.startsWith("rk_"))) {
            throw new ProviderValidationException("Stripe secretKey must be a Stripe secret or restricted API key.");
        }

        final String webhookSecret = config.get(WEBHOOK_SECRET);
        if (webhookSecret == null || !webhookSecret.startsWith("whsec_")) {
            throw new ProviderValidationException("Stripe webhookSecret must be a Stripe webhook signing secret.");
        }
    }

    @Override
    public Optional<CheckoutSessionDraft> prepareCheckoutSession(final CheckoutPreparationContext context) {
        validateConfig(context.provider().config());
        requireCurrency(context.payment().purchaseOrder());
        requirePositiveGrossAmount(context.payment().purchaseOrder());

        final Map<String, Object> checkout = context.request().checkout();
        final String returnUrl = requireAbsoluteUri(checkout, RETURN_URL);
        final String cancelUrl = requireAbsoluteUri(checkout, CANCEL_URL);
        return Optional.of(new CheckoutSessionDraft(
                Map.of(RETURN_URL, returnUrl, CANCEL_URL, cancelUrl),
                null));
    }

    @Override
    public PaymentResult execute(final PaymentContext context) {
        if (context.checkout() == null) {
            throw new ProviderValidationException("Stripe checkout session is required.");
        }

        final SessionCreateParams params = checkoutSessionParams(context);
        final Session session;
        try {
            final StripeClient client = stripeClient(context.provider().config());
            session = client.v1().checkout().sessions().create(params, requestOptions(context.transaction().id()));
        } catch (StripeException ex) {
            throw new ProviderExecutionException(
                    classifyExecutionFailure(ex),
                    "Stripe Checkout session creation failed.",
                    ex);
        }

        if (session == null || isBlank(session.getId()) || isBlank(session.getUrl())) {
            throw new ProviderExecutionException(
                    INVALID_RESPONSE,
                    "Stripe returned an incomplete Checkout session.");
        }

        return new PaymentResult(
                provider(),
                PaymentTransactionStatus.PENDING,
                sessionData(session),
                new StatusDetails(AWAITING_CUSTOMER, "Stripe Checkout is waiting for customer completion."),
                new PaymentNextAction(PaymentNextActionType.REDIRECT, Map.of("url", session.getUrl())));
    }

    static ProviderExecutionFailure classifyExecutionFailure(final StripeException exception) {
        return exception instanceof AuthenticationException
                ? AUTHENTICATION_FAILED
                : UNAVAILABLE;
    }

    @Override
    public PaymentResult completeCheckout(final ProviderCheckoutContext context) {
        final String sessionId = requireQueryParam(context, STRIPE_SESSION_ID);
        final Session session;
        try {
            final StripeClient client = stripeClient(context.provider().config());
            session = client.v1().checkout().sessions().retrieve(sessionId);
        } catch (StripeException ex) {
            throw new ProviderExecutionException(
                    classifyExecutionFailure(ex),
                    "Stripe Checkout session retrieval failed.",
                    ex);
        }

        ensureSessionMatchesTransaction(session, context.transaction().id());
        final PaymentTransactionStatus status = paymentStatus(session.getPaymentStatus());
        return new PaymentResult(
                provider(),
                status,
                sessionData(session),
                checkoutStatusDetails(status, session.getPaymentStatus()),
                redirectToCheckoutPayload(context, RETURN_URL));
    }

    @Override
    public PaymentResult cancelCheckout(final ProviderCheckoutContext context) {
        return new PaymentResult(
                provider(),
                PaymentTransactionStatus.FAILED,
                Map.of("status", "CANCELLED"),
                new StatusDetails(CANCELLED, "Stripe Checkout was cancelled by the customer."),
                redirectToCheckoutPayload(context, CANCEL_URL));
    }

    @Override
    public UUID extractPaymentTransactionId(final WebhookRequest request) {
        try {
            return transactionId(payloadObject(request.body()));
        } catch (WebhookRejectedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WebhookRejectedException("Stripe webhook payload is invalid.", ex);
        }
    }

    @Override
    public PaymentWebhookResult handleWebhook(final PaymentWebhookContext context) {
        final String signature = firstHeader(context.request(), STRIPE_SIGNATURE)
                .orElseThrow(() -> new WebhookRejectedException("Stripe webhook signature is missing."));
        final String webhookSecret = requiredConfig(context.provider().config(), WEBHOOK_SECRET);
        final Event event;
        try {
            event = Webhook.constructEvent(context.request().body(), signature, webhookSecret);
        } catch (SignatureVerificationException | JsonParseException ex) {
            throw new WebhookRejectedException("Stripe webhook verification failed.", ex);
        }

        final JsonObject payload = payloadObject(context.request().body());
        final UUID verifiedTransactionId = transactionId(payload);
        if (!context.transaction().id().equals(verifiedTransactionId)) {
            throw new WebhookRejectedException("Stripe webhook transaction does not match restored transaction.");
        }

        final String eventType = event.getType();
        final JsonObject stripeObject = stripeObject(payload);
        final PaymentTransactionStatus status = webhookStatus(eventType, string(stripeObject, "payment_status"));
        return new PaymentWebhookResult(
                provider(),
                status,
                webhookData(event, stripeObject),
                webhookStatusDetails(eventType, status));
    }

    private SessionCreateParams checkoutSessionParams(final PaymentContext context) {
        final PurchaseOrder purchaseOrder = context.payment().purchaseOrder();
        final long amount = requirePositiveGrossAmount(purchaseOrder);
        final String currency = requireCurrency(purchaseOrder).toLowerCase(Locale.ROOT);
        final String transactionId = context.transaction().id().toString();
        final String description = isBlank(context.payment().description())
                ? "Payment " + context.payment().id()
                : context.payment().description();
        final String productName = limit(description, MAX_PRODUCT_NAME_LENGTH);

        final SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(productName)
                        .build();
        final SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency)
                        .setUnitAmount(amount)
                        .setProductData(productData)
                        .build();
        final SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(priceData)
                .build();
        final SessionCreateParams.PaymentIntentData paymentIntentData =
                SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata(PAYMENT_TRANSACTION_ID, transactionId)
                        .build();

        final SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(transactionId)
                .putMetadata(PAYMENT_TRANSACTION_ID, transactionId)
                .setPaymentIntentData(paymentIntentData)
                .setSuccessUrl(addQueryParam(
                        context.checkout().urls().returnUrl(),
                        STRIPE_SESSION_ID,
                        CHECKOUT_SESSION_PLACEHOLDER))
                .setCancelUrl(context.checkout().urls().cancelUrl())
                .addLineItem(lineItem);

        final BillingInfo billingInfo = context.payment().billingInfo();
        if (billingInfo != null && !isBlank(billingInfo.getEmail())) {
            builder.setCustomerEmail(billingInfo.getEmail());
        }
        return builder.build();
    }

    private StripeClient stripeClient(final Map<String, String> config) {
        return clientFactory.create(requiredConfig(config, SECRET_KEY));
    }

    private static RequestOptions requestOptions(final UUID idempotencyKey) {
        return RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey.toString())
                .build();
    }

    private static void ensureSessionMatchesTransaction(final Session session, final UUID transactionId) {
        if (session == null) {
            throw new ProviderValidationException("Stripe Checkout session was not found.");
        }
        final String expected = transactionId.toString();
        final String metadataId = session.getMetadata() != null
                ? session.getMetadata().get(PAYMENT_TRANSACTION_ID)
                : null;
        if (!expected.equals(metadataId) && !expected.equals(session.getClientReferenceId())) {
            throw new ProviderValidationException("Stripe Checkout session does not match payment transaction.");
        }
    }

    private static Map<String, Object> sessionData(final Session session) {
        final Map<String, Object> data = new LinkedHashMap<>();
        putIfPresent(data, "checkoutSessionId", session.getId());
        putIfPresent(data, "paymentIntentId", session.getPaymentIntent());
        putIfPresent(data, "paymentStatus", session.getPaymentStatus());
        putIfPresent(data, "status", session.getStatus());
        return Map.copyOf(data);
    }

    private static Map<String, Object> webhookData(final Event event, final JsonObject stripeObject) {
        final Map<String, Object> data = new LinkedHashMap<>();
        putIfPresent(data, "eventId", event.getId());
        putIfPresent(data, "eventType", event.getType());
        putIfPresent(data, "stripeObjectId", string(stripeObject, "id"));
        putIfPresent(data, "paymentIntentId", string(stripeObject, "payment_intent"));
        putIfPresent(data, "paymentStatus", string(stripeObject, "payment_status"));
        putIfPresent(data, "status", string(stripeObject, "status"));
        return Map.copyOf(data);
    }

    private static PaymentTransactionStatus webhookStatus(final String eventType, final String paymentStatus) {
        return switch (eventType) {
            case "checkout.session.async_payment_succeeded" ->
                    PaymentTransactionStatus.SUCCESS;
            case "checkout.session.async_payment_failed", "checkout.session.expired" ->
                    PaymentTransactionStatus.FAILED;
            case "checkout.session.completed" -> paymentStatus(paymentStatus);
            default -> throw new WebhookRejectedException("Unsupported Stripe webhook event: " + eventType);
        };
    }

    private static PaymentTransactionStatus paymentStatus(final String paymentStatus) {
        final List<String> successStatuses = List.of(PAID, NO_PAYMENT_REQUIRED);

        return successStatuses.contains(paymentStatus)
                ? PaymentTransactionStatus.SUCCESS
                : PaymentTransactionStatus.PENDING;
    }

    private static StatusDetails checkoutStatusDetails(
            final PaymentTransactionStatus status,
            final String stripeStatus) {
        return new StatusDetails(
                PaymentTransactionStatus.SUCCESS.equals(status) ? COMPLETED : PROCESSING,
                PaymentTransactionStatus.SUCCESS.equals(status)
                        ? "Stripe Checkout payment completed."
                        : "Stripe Checkout payment is still processing: " + stripeStatus);
    }

    private static StatusDetails webhookStatusDetails(
            final String eventType,
            final PaymentTransactionStatus status) {
        final String code = switch (eventType) {
            case "checkout.session.async_payment_succeeded" -> COMPLETED;
            case "checkout.session.async_payment_failed" -> PAYMENT_FAILED;
            case "checkout.session.expired" -> EXPIRED;
            default -> PaymentTransactionStatus.SUCCESS.equals(status) ? COMPLETED : PROCESSING;
        };
        return new StatusDetails(code, "Stripe webhook mapped to payment status " + status + ".");
    }

    private static JsonObject payloadObject(final String body) {
        if (isBlank(body)) {
            throw new WebhookRejectedException("Stripe webhook payload is empty.");
        }
        try {
            final JsonElement payload = JsonParser.parseString(body);
            if (!payload.isJsonObject()) {
                throw new WebhookRejectedException("Stripe webhook payload is invalid.");
            }
            return payload.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            throw new WebhookRejectedException("Stripe webhook payload is invalid.", ex);
        }
    }

    private static UUID transactionId(final JsonObject payload) {
        final String value = string(metadata(stripeObject(payload)), PAYMENT_TRANSACTION_ID);
        if (isBlank(value)) {
            throw new WebhookRejectedException("Stripe webhook does not contain paymentTransactionId.");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new WebhookRejectedException("Stripe webhook contains an invalid paymentTransactionId.", ex);
        }
    }

    private static JsonObject stripeObject(final JsonObject payload) {
        return object(object(payload, "data"), "object");
    }

    private static JsonObject metadata(final JsonObject stripeObject) {
        return object(stripeObject, "metadata");
    }

    private static JsonObject object(final JsonObject source, final String name) {
        if (source == null || !source.has(name) || !source.get(name).isJsonObject()) {
            throw new WebhookRejectedException("Stripe webhook payload is missing " + name + ".");
        }
        return source.getAsJsonObject(name);
    }

    private static String string(final JsonObject source, final String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()
                || !source.get(name).isJsonPrimitive()) {
            return null;
        }
        return source.get(name).getAsString();
    }

    private static Optional<String> firstHeader(final WebhookRequest request, final String name) {
        if (request.headers() == null) {
            return Optional.empty();
        }
        return request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .filter(value -> !isBlank(value))
                .findFirst();
    }

    private static PaymentNextAction redirectToCheckoutPayload(
            final ProviderCheckoutContext context,
            final String key) {
        final Object value = context.checkoutSession().payload() != null
                ? context.checkoutSession().payload().get(key)
                : null;
        if (!(value instanceof String url) || isBlank(url)) {
            throw new ProviderValidationException("Stripe checkout session is missing " + key + ".");
        }
        return new PaymentNextAction(PaymentNextActionType.REDIRECT, Map.of("url", url));
    }

    private static String requireQueryParam(final ProviderCheckoutContext context, final String name) {
        final List<String> values = context.queryParams() != null ? context.queryParams().get(name) : null;
        if (values == null || values.isEmpty() || isBlank(values.getFirst())) {
            throw new ProviderValidationException("Stripe checkout callback requires " + name + ".");
        }
        return values.getFirst();
    }

    private static String requireAbsoluteUri(final Map<String, Object> source, final String name) {
        final Object value = source != null ? source.get(name) : null;
        if (!(value instanceof String text) || isBlank(text)) {
            throw new ProviderValidationException("Stripe checkout requires " + name + ".");
        }
        try {
            final URI uri = new URI(text);
            if (!uri.isAbsolute()) {
                throw new ProviderValidationException("Stripe checkout " + name + " must be an absolute URL.");
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            throw new ProviderValidationException("Stripe checkout " + name + " must be an absolute URL.", ex);
        }
    }

    private static String requireCurrency(final PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || isBlank(purchaseOrder.getCurrency())) {
            throw new ProviderValidationException("Stripe payment requires purchaseOrder.currency.");
        }
        return purchaseOrder.getCurrency();
    }

    private static long requirePositiveGrossAmount(final PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getGrossAmount() == null
                || purchaseOrder.getGrossAmount() <= 0) {
            throw new ProviderValidationException(
                    "Stripe payment requires a positive purchaseOrder.grossAmount.");
        }
        return purchaseOrder.getGrossAmount();
    }

    private static String requiredConfig(final Map<String, String> config, final String key) {
        final String value = config != null ? config.get(key) : null;
        if (isBlank(value)) {
            throw new ProviderValidationException("Stripe provider configuration requires " + key + ".");
        }
        return value;
    }

    private static String addQueryParam(final String url, final String name, final String value) {
        final int fragmentIndex = url.indexOf('#');
        final String base = fragmentIndex >= 0 ? url.substring(0, fragmentIndex) : url;
        final String fragment = fragmentIndex >= 0 ? url.substring(fragmentIndex) : "";
        final String separator = base.contains("?") ? "&" : "?";
        return base + separator + name + "=" + value + fragment;
    }

    private static void putIfPresent(final Map<String, Object> target, final String key, final Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String limit(final String value, final int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
