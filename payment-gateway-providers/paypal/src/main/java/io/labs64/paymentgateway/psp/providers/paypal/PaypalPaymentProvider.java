package io.labs64.paymentgateway.psp.providers.paypal;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.Address;
import com.paypal.sdk.models.AmountBreakdown;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.CaptureStatus;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.FulfillmentType;
import com.paypal.sdk.models.ItemCategory;
import com.paypal.sdk.models.ItemRequest;
import com.paypal.sdk.models.LinkDescription;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.Name;
import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderApplicationContext;
import com.paypal.sdk.models.OrderApplicationContextShippingPreference;
import com.paypal.sdk.models.OrderApplicationContextUserAction;
import com.paypal.sdk.models.OrderCaptureRequest;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.OrdersCapture;
import com.paypal.sdk.models.Payer;
import com.paypal.sdk.models.PhoneNumber;
import com.paypal.sdk.models.PhoneType;
import com.paypal.sdk.models.PhoneWithType;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.paypal.sdk.models.ShippingDetails;
import com.paypal.sdk.models.ShippingName;
import io.labs64.paymentgateway.model.BillingInfo;
import io.labs64.paymentgateway.model.OrderItem;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.model.ShippingInfo;
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
import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import io.labs64.paymentgateway.psp.spi.ProviderWebhookSupport;
import io.labs64.paymentgateway.psp.spi.StatusDetails;
import io.labs64.paymentgateway.psp.spi.WebhookRejectedException;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import org.apache.commons.lang3.StringUtils;

import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.AWAITING_CUSTOMER;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.CANCELLED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.COMPLETED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.DECLINED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.PAYMENT_FAILED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.PROCESSING;
import static io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.INVALID_RESPONSE;
import static io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE;

/**
 * PayPal payment provider.
 * <p>
 * This class owns the PayPal tenant configuration contract, browser checkout,
 * order capture, and verified webhook handling.
 */
public class PaypalPaymentProvider implements PaymentProvider, ProviderConfigSupport,
        ProviderCheckoutSupport, ProviderWebhookSupport {

    public static final String PROVIDER = "paypal";

    // configuration
    private static final String CLIENT_ID = "clientId";
    private static final String CLIENT_SECRET = "clientSecret";
    private static final String ENVIRONMENT = "environment";
    private static final String WEBHOOK_ID = "webhookId";

    // environment
    private static final String SANDBOX = "sandbox";
    private static final String LIVE = "live";

    // checkout fields
    private static final String RETURN_URL = "returnUrl";
    private static final String CANCEL_URL = "cancelUrl";
    private static final String ORDER_ID = "orderId";
    private static final String ORDER_APPROVED = "CHECKOUT.ORDER.APPROVED";
    private static final String APPROVE_REL = "approve";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String PREFER_REPRESENTATION = "return=representation";
    private static final int PAYPAL_MAX_DESCRIPTION_LENGTH = 127;
    private static final int PAYPAL_MAX_ITEM_NAME_LENGTH = 127;
    private static final int PAYPAL_MAX_ITEM_DESCRIPTION_LENGTH = 127;
    private static final int PAYPAL_MAX_SKU_LENGTH = 127;

    private static final Set<ProviderConfigField> CONFIG_FIELDS = Set.of(
            ProviderConfigField.required(CLIENT_ID),
            ProviderConfigField.required(CLIENT_SECRET),
            ProviderConfigField.required(ENVIRONMENT),
            ProviderConfigField.required(WEBHOOK_ID));

    private final PaypalClientFactory clientFactory;
    private final PaypalWebhookVerifier webhookVerifier;

    public PaypalPaymentProvider(final PaypalClientFactory clientFactory) {
        this(clientFactory, new PaypalApiWebhookVerifier(clientFactory));
    }

    PaypalPaymentProvider(
            final PaypalClientFactory clientFactory,
            final PaypalWebhookVerifier webhookVerifier) {
        this.clientFactory = clientFactory;
        this.webhookVerifier = webhookVerifier;
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
        final String environment = normalizedEnvironment(config.get(ENVIRONMENT));
        if (!SANDBOX.equals(environment) && !LIVE.equals(environment)) {
            throw new ProviderValidationException("PayPal environment must be either sandbox or live.");
        }
    }

    @Override
    public Optional<CheckoutSessionDraft> prepareCheckoutSession(final CheckoutPreparationContext context) {
        validateConfig(context.provider().config());
        requireProviderConfig(context.provider().config(), CLIENT_ID);
        requireProviderConfig(context.provider().config(), CLIENT_SECRET);
        final PurchaseOrder purchaseOrder = context.payment().purchaseOrder();
        final List<ItemRequest> items = toItems(
                purchaseOrder,
                hasShippingInfo(context.payment().shippingInfo()));
        toAmount(purchaseOrder, items);

        final Map<String, Object> checkout = context.request().checkout();
        final String returnUrl = requireAbsoluteUri(checkout, RETURN_URL);
        final String cancelUrl = requireAbsoluteUri(checkout, CANCEL_URL);

        return Optional.of(new CheckoutSessionDraft(Map.of(RETURN_URL, returnUrl, CANCEL_URL, cancelUrl), null));
    }

    @Override
    public PaymentResult execute(final PaymentContext context) {
        if (context.checkout() == null) {
            throw new ProviderValidationException("PayPal checkout session is required.");
        }

        final Order order = createOrder(context);
        final String orderId = requireOrderId(order);
        final String approveUrl = requireApproveUrl(order);

        return new PaymentResult(
                provider(),
                PaymentTransactionStatus.PENDING,
                Map.of(ORDER_ID, orderId, "status", status(order)),
                new StatusDetails(AWAITING_CUSTOMER, "PayPal order is waiting for buyer approval."),
                new PaymentNextAction(PaymentNextActionType.REDIRECT, Map.of("url", approveUrl)));
    }

    @Override
    public PaymentResult completeCheckout(final ProviderCheckoutContext context) {
        final String orderId = requireQueryParam(context, "token");
        final Order order = captureOrder(context, orderId);
        final OrdersCapture capture = requireCapture(order);
        final PaymentTransactionStatus status = captureStatus(capture.getStatus());

        return new PaymentResult(
                provider(),
                status,
                captureData(orderId, order, capture),
                toStatusDetails(capture),
                redirectToSessionPayload(context, RETURN_URL));
    }

    @Override
    public PaymentResult cancelCheckout(final ProviderCheckoutContext context) {
        final String orderId = firstQueryParam(context, "token").orElse(null);
        final Map<String, Object> pspData = orderId == null
                ? Map.of()
                : Map.of(ORDER_ID, orderId, "status", "CANCELLED");

        return new PaymentResult(
                provider(),
                PaymentTransactionStatus.FAILED,
                pspData,
                new StatusDetails(CANCELLED, "PayPal checkout was cancelled by the buyer."),
                redirectToSessionPayload(context, CANCEL_URL));
    }

    @Override
    public UUID extractPaymentTransactionId(final WebhookRequest request) {
        try {
            return transactionId(payload(request.body()));
        } catch (WebhookRejectedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WebhookRejectedException("PayPal webhook payload is invalid.", ex);
        }
    }

    @Override
    public PaymentWebhookResult handleWebhook(final PaymentWebhookContext context) {
        final String webhookId = requiredConfig(context.provider().config(), WEBHOOK_ID);
        webhookVerifier.verify(client(context.provider().config()), webhookId, context.request());

        final JsonObject payload = payload(context.request().body());
        final UUID verifiedTransactionId = transactionId(payload);
        if (!context.transaction().id().equals(verifiedTransactionId)) {
            throw new WebhookRejectedException("PayPal webhook transaction does not match restored transaction.");
        }

        final String eventType = requiredString(payload, "event_type");
        final JsonObject resource = requiredObject(payload, "resource");
        if (ORDER_APPROVED.equals(eventType)) {
            return captureApprovedOrder(context, payload, resource);
        }

        final PaymentTransactionStatus status = webhookStatus(eventType);
        return new PaymentWebhookResult(
                provider(),
                status,
                webhookData(payload, resource),
                webhookStatusDetails(eventType, status));
    }

    private Order createOrder(final PaymentContext context) {
        final CreateOrderInput input = new CreateOrderInput.Builder(CONTENT_TYPE_JSON, toOrderRequest(context))
                .prefer(PREFER_REPRESENTATION)
                .paypalRequestId(context.transaction().id().toString())
                .build();

        try {
            final ApiResponse<Order> response = client(context.provider().config())
                    .getOrdersController()
                    .createOrder(input);
            return response.getResult();
        } catch (com.paypal.sdk.exceptions.ApiException | IOException ex) {
            throw new ProviderExecutionException(
                    UNAVAILABLE,
                    "PayPal order creation failed.",
                    ex);
        }
    }

    private Order captureOrder(final ProviderCheckoutContext context, final String orderId) {
        return captureOrder(
                context.provider().config(),
                context.transaction().id(),
                orderId);
    }

    private Order captureOrder(
            final Map<String, String> config,
            final UUID transactionId,
            final String orderId) {
        final CaptureOrderInput input = new CaptureOrderInput.Builder(orderId, CONTENT_TYPE_JSON)
                .prefer(PREFER_REPRESENTATION)
                .paypalRequestId(transactionId.toString())
                .body(new OrderCaptureRequest.Builder().build())
                .build();

        try {
            final ApiResponse<Order> response = client(config)
                    .getOrdersController()
                    .captureOrder(input);
            return response.getResult();
        } catch (com.paypal.sdk.exceptions.ApiException | IOException ex) {
            throw new ProviderExecutionException(
                    UNAVAILABLE,
                    "PayPal order capture failed.",
                    ex);
        }
    }

    private OrderRequest toOrderRequest(final PaymentContext context) {
        final OrderRequest.Builder builder = new OrderRequest.Builder(
                CheckoutPaymentIntent.CAPTURE,
                List.of(toPurchaseUnit(context)))
                .applicationContext(toApplicationContext(
                        context,
                        hasShippingInfo(context.payment().shippingInfo())))
                .payer(toPayer(context.payment().billingInfo()));
        return builder.build();
    }

    private PurchaseUnitRequest toPurchaseUnit(final PaymentContext context) {
        final PurchaseOrder purchaseOrder = context.payment().purchaseOrder();
        final List<ItemRequest> items = toItems(purchaseOrder, hasShippingInfo(context.payment().shippingInfo()));
        final PurchaseUnitRequest.Builder builder = new PurchaseUnitRequest.Builder(toAmount(purchaseOrder, items))
                .referenceId(context.transaction().id().toString())
                .customId(context.payment().id().toString())
                .invoiceId(context.transaction().id().toString())
                .description(limit(context.payment().description(), PAYPAL_MAX_DESCRIPTION_LENGTH));

        if (!items.isEmpty()) {
            builder.items(items);
        }

        final ShippingDetails shipping = toShipping(context.payment().shippingInfo());
        if (shipping != null) {
            builder.shipping(shipping);
        }

        return builder.build();
    }

    private AmountWithBreakdown toAmount(final PurchaseOrder purchaseOrder, final List<ItemRequest> items) {
        final String currency = requireCurrency(purchaseOrder);
        final BigDecimal grossAmount = requirePositiveGrossAmount(purchaseOrder);
        final AmountWithBreakdown.Builder builder = new AmountWithBreakdown.Builder(
                currency,
                toMajorUnits(grossAmount));

        final AmountBreakdown breakdown = toBreakdown(purchaseOrder, currency, items);
        if (breakdown != null) {
            builder.breakdown(breakdown);
        }

        return builder.build();
    }

    private AmountBreakdown toBreakdown(
            final PurchaseOrder purchaseOrder,
            final String currency,
            final List<ItemRequest> items) {
        if (items.isEmpty()) {
            return null;
        }

        final BigDecimal itemTotal = items.stream()
                .map(item -> fromMajorUnits(item.getUnitAmount().getValue())
                        .multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal taxTotal = purchaseOrder.getTaxAmount() != null
                ? BigDecimal.valueOf(purchaseOrder.getTaxAmount())
                : BigDecimal.ZERO;
        final BigDecimal grossAmount = requirePositiveGrossAmount(purchaseOrder);
        if (itemTotal.add(taxTotal).compareTo(grossAmount) != 0) {
            throw new ProviderValidationException("PayPal payment requires purchaseOrder items and taxAmount to match grossAmount.");
        }

        final AmountBreakdown.Builder builder = new AmountBreakdown.Builder()
                .itemTotal(toMoney(currency, itemTotal));
        if (taxTotal.compareTo(BigDecimal.ZERO) > 0) {
            builder.taxTotal(toMoney(currency, taxTotal));
        }
        return builder.build();
    }

    private List<ItemRequest> toItems(final PurchaseOrder purchaseOrder, final boolean physicalGoods) {
        if (purchaseOrder == null || purchaseOrder.getItems() == null || purchaseOrder.getItems().isEmpty()) {
            return List.of();
        }

        return purchaseOrder.getItems().stream()
                .map(item -> toItem(item, requireCurrency(purchaseOrder), physicalGoods))
                .toList();
    }

    private ItemRequest toItem(
            final OrderItem item,
            final String currency,
            final boolean physicalGoods) {
        final String name = limit(
                requireItemName(item),
                PAYPAL_MAX_ITEM_NAME_LENGTH);
        final ItemRequest.Builder builder = new ItemRequest.Builder(
                name,
                toMoney(currency, requireItemPrice(item)),
                String.valueOf(requireItemQuantity(item)))
                .category(physicalGoods ? ItemCategory.PHYSICAL_GOODS : ItemCategory.DIGITAL_GOODS);

        optionalString(item.getDescription()).ifPresent(value ->
                builder.description(limit(value, PAYPAL_MAX_ITEM_DESCRIPTION_LENGTH)));
        optionalString(item.getSku()).ifPresent(value -> builder.sku(limit(value, PAYPAL_MAX_SKU_LENGTH)));
        if (item.getUrl() != null) {
            builder.url(item.getUrl().toString());
        }
        if (item.getImage() != null) {
            builder.imageUrl(item.getImage().toString());
        }

        return builder.build();
    }

    private Payer toPayer(final BillingInfo billingInfo) {
        if (!hasBillingInfo(billingInfo)) {
            return null;
        }

        final Payer.Builder builder = new Payer.Builder();
        optionalString(billingInfo.getEmail()).ifPresent(builder::emailAddress);
        toName(billingInfo).ifPresent(builder::name);
        toPayerPhone(billingInfo).ifPresent(builder::phone);
        toAddress(billingInfo).ifPresent(builder::address);
        return builder.build();
    }

    private ShippingDetails toShipping(final ShippingInfo shippingInfo) {
        if (!hasShippingInfo(shippingInfo)) {
            return null;
        }

        final ShippingDetails.Builder builder = new ShippingDetails.Builder()
                .type(FulfillmentType.SHIPPING);
        toShippingName(shippingInfo).ifPresent(builder::name);
        optionalString(shippingInfo.getEmail()).ifPresent(builder::emailAddress);
        toAddress(shippingInfo).ifPresent(builder::address);
        return builder.build();
    }

    private static Optional<Name> toName(final BillingInfo source) {
        final Optional<String> firstName = optionalString(source.getFirstName());
        final Optional<String> lastName = optionalString(source.getLastName());
        if (firstName.isEmpty() && lastName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Name.Builder()
                .givenName(firstName.orElse(null))
                .surname(lastName.orElse(null))
                .build());
    }

    private static Optional<ShippingName> toShippingName(final ShippingInfo source) {
        final String fullName = Stream.of(
                        optionalString(source.getFirstName()).orElse(""),
                        optionalString(source.getLastName()).orElse(""))
                .filter(StringUtils::isNotBlank)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        return StringUtils.isBlank(fullName)
                ? Optional.empty()
                : Optional.of(new ShippingName.Builder().fullName(fullName).build());
    }

    private static Optional<Address> toAddress(final BillingInfo source) {
        return toAddress(
                source.getCountry(), source.getAddress1(), source.getAddress2(),
                source.getCity(), source.getState(), source.getPostalCode());
    }

    private static Optional<Address> toAddress(final ShippingInfo source) {
        return toAddress(
                source.getCountry(), source.getAddress1(), source.getAddress2(),
                source.getCity(), source.getState(), source.getPostalCode());
    }

    private static Optional<Address> toAddress(
            final String countryValue,
            final String address1,
            final String address2,
            final String city,
            final String state,
            final String postalCode) {
        final Optional<String> country = optionalString(countryValue);
        if (country.isEmpty()) {
            return Optional.empty();
        }

        final Address.Builder builder = new Address.Builder(country.get());
        optionalString(address1).ifPresent(builder::addressLine1);
        optionalString(address2).ifPresent(builder::addressLine2);
        optionalString(city).ifPresent(builder::adminArea2);
        optionalString(state).ifPresent(builder::adminArea1);
        optionalString(postalCode).ifPresent(builder::postalCode);
        return Optional.of(builder.build());
    }

    private static Optional<PhoneWithType> toPayerPhone(final BillingInfo source) {
        return optionalString(source.getPhone())
                .map(phone -> new PhoneWithType.Builder(new PhoneNumber.Builder(phone).build())
                        .phoneType(PhoneType.MOBILE)
                        .build());
    }

    private static boolean hasShippingInfo(final ShippingInfo shippingInfo) {
        return shippingInfo != null && Stream.of(
                        shippingInfo.getFirstName(), shippingInfo.getLastName(), shippingInfo.getEmail(),
                        shippingInfo.getPhone(), shippingInfo.getCity(), shippingInfo.getCountry(),
                        shippingInfo.getAddress1(), shippingInfo.getAddress2(), shippingInfo.getPostalCode(),
                        shippingInfo.getState())
                .anyMatch(StringUtils::isNotBlank);
    }

    private static boolean hasBillingInfo(final BillingInfo billingInfo) {
        return billingInfo != null && Stream.of(
                        billingInfo.getFirstName(), billingInfo.getLastName(), billingInfo.getEmail(),
                        billingInfo.getPhone(), billingInfo.getCity(), billingInfo.getCountry(),
                        billingInfo.getAddress1(), billingInfo.getAddress2(), billingInfo.getPostalCode(),
                        billingInfo.getState(), billingInfo.getVatId())
                .anyMatch(StringUtils::isNotBlank);
    }

    private OrderApplicationContext toApplicationContext(
            final PaymentContext context,
            final boolean hasShippingInfo) {
        return new OrderApplicationContext.Builder()
                .returnUrl(context.checkout().urls().returnUrl())
                .cancelUrl(context.checkout().urls().cancelUrl())
                .shippingPreference(hasShippingInfo
                        ? OrderApplicationContextShippingPreference.SET_PROVIDED_ADDRESS
                        : OrderApplicationContextShippingPreference.NO_SHIPPING)
                .userAction(OrderApplicationContextUserAction.PAY_NOW)
                .build();
    }

    private PaypalServerSdkClient client(final Map<String, String> config) {
        return clientFactory.create(
                config.get(CLIENT_ID),
                config.get(CLIENT_SECRET),
                toEnvironment(config.get(ENVIRONMENT)));
    }

    private static Environment toEnvironment(final String value) {
        return LIVE.equals(normalizedEnvironment(value)) ? Environment.PRODUCTION : Environment.SANDBOX;
    }

    private static String requireOrderId(final Order order) {
        if (order == null || StringUtils.isBlank(order.getId())) {
            throw new ProviderExecutionException(
                    INVALID_RESPONSE,
                    "PayPal order creation returned no order id.");
        }
        return order.getId();
    }

    private static String requireApproveUrl(final Order order) {
        if (order == null || order.getLinks() == null) {
            throw new ProviderExecutionException(
                    INVALID_RESPONSE,
                    "PayPal order creation returned no approval link.");
        }
        return order.getLinks().stream()
                .filter(link -> APPROVE_REL.equalsIgnoreCase(link.getRel()))
                .map(LinkDescription::getHref)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElseThrow(() -> new ProviderExecutionException(
                        INVALID_RESPONSE,
                        "PayPal order creation returned no approval link."));
    }

    private static OrdersCapture requireCapture(final Order order) {
        if (order == null || order.getPurchaseUnits() == null) {
            throw new ProviderExecutionException(
                    INVALID_RESPONSE,
                    "PayPal order capture returned no capture result.");
        }
        return order.getPurchaseUnits().stream()
                .filter(unit -> unit != null
                        && unit.getPayments() != null
                        && unit.getPayments().getCaptures() != null)
                .flatMap(unit -> unit.getPayments().getCaptures().stream())
                .findFirst()
                .orElseThrow(() -> new ProviderExecutionException(
                        INVALID_RESPONSE,
                        "PayPal order capture returned no capture result."));
    }

    private static PaymentTransactionStatus captureStatus(final CaptureStatus status) {
        if (status == null) {
            throw new ProviderExecutionException(
                    INVALID_RESPONSE,
                    "PayPal order capture returned no capture status.");
        }
        return switch (status) {
            case COMPLETED -> PaymentTransactionStatus.SUCCESS;
            case PENDING -> PaymentTransactionStatus.PENDING;
            case DECLINED, FAILED -> PaymentTransactionStatus.FAILED;
            case PARTIALLY_REFUNDED, REFUNDED, _UNKNOWN -> throw new ProviderExecutionException(
                    INVALID_RESPONSE,
                    "PayPal order capture returned an unsupported capture status.");
        };
    }

    private static StatusDetails toStatusDetails(final OrdersCapture capture) {
        return switch (capture.getStatus()) {
            case COMPLETED -> new StatusDetails(COMPLETED, "PayPal payment was captured successfully.");
            case PENDING -> new StatusDetails(PROCESSING, "PayPal payment capture is processing.");
            case DECLINED -> new StatusDetails(DECLINED, "PayPal payment capture was declined.");
            case FAILED -> new StatusDetails(PAYMENT_FAILED, "PayPal payment capture failed.");
            case PARTIALLY_REFUNDED, REFUNDED, _UNKNOWN -> throw new ProviderExecutionException(
                    INVALID_RESPONSE,
                    "PayPal order capture returned an unsupported capture status.");
        };
    }

    private static Map<String, Object> captureData(
            final String orderId,
            final Order order,
            final OrdersCapture capture) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put(ORDER_ID, orderId);
        putIfPresent(data, "status", status(order));
        putIfPresent(data, "captureId", capture.getId());
        putIfPresent(data, "captureStatus", capture.getStatus() != null ? capture.getStatus().toString() : null);
        return Map.copyOf(data);
    }

    private static PaymentNextAction redirectToSessionPayload(
            final ProviderCheckoutContext context,
            final String field) {
        final Map<String, Object> payload = context.checkoutSession() != null
                ? context.checkoutSession().payload()
                : null;
        final Object url = payload != null ? payload.get(field) : null;
        if (!(url instanceof String stringUrl) || StringUtils.isBlank(stringUrl)) {
            throw new ProviderValidationException("PayPal checkout session payload requires " + field + ".");
        }
        return new PaymentNextAction(PaymentNextActionType.REDIRECT, Map.of("url", stringUrl.trim()));
    }

    private static String status(final Order order) {
        return order != null && order.getStatus() != null ? order.getStatus().toString() : "UNKNOWN";
    }

    private static String requireCurrency(final PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || StringUtils.isBlank(purchaseOrder.getCurrency())) {
            throw new ProviderValidationException("PayPal payment requires purchaseOrder.currency.");
        }
        return purchaseOrder.getCurrency().trim();
    }

    private static BigDecimal requirePositiveGrossAmount(final PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getGrossAmount() == null
                || purchaseOrder.getGrossAmount() <= 0) {
            throw new ProviderValidationException(
                    "PayPal payment requires a positive purchaseOrder.grossAmount.");
        }
        return BigDecimal.valueOf(purchaseOrder.getGrossAmount());
    }

    private static String requireItemName(final OrderItem item) {
        if (item == null || StringUtils.isBlank(item.getName())) {
            throw new ProviderValidationException("PayPal payment requires purchaseOrder.items[].name.");
        }
        return item.getName().trim();
    }

    private static BigDecimal requireItemPrice(final OrderItem item) {
        if (item == null || item.getPrice() == null) {
            throw new ProviderValidationException("PayPal payment requires purchaseOrder.items[].price.");
        }
        return BigDecimal.valueOf(item.getPrice());
    }

    private static int requireItemQuantity(final OrderItem item) {
        if (item == null || item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new ProviderValidationException("PayPal payment requires purchaseOrder.items[].quantity.");
        }
        return item.getQuantity();
    }

    private static Optional<String> optionalString(final String value) {
        return StringUtils.isBlank(value) ? Optional.empty() : Optional.of(value.trim());
    }

    private static Money toMoney(final String currency, final BigDecimal minorUnits) {
        return new Money.Builder(currency, toMajorUnits(minorUnits)).build();
    }

    private static String toMajorUnits(final BigDecimal minorUnits) {
        return minorUnits.movePointLeft(2)
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private static BigDecimal fromMajorUnits(final String majorUnits) {
        return new BigDecimal(majorUnits).movePointRight(2);
    }

    private static String limit(final String value, final int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String requireQueryParam(final ProviderCheckoutContext context, final String field) {
        return firstQueryParam(context, field)
                .orElseThrow(() -> new ProviderValidationException(
                        "PayPal checkout callback requires query parameter: " + field));
    }

    private static Optional<String> firstQueryParam(final ProviderCheckoutContext context, final String field) {
        final List<String> values = context.queryParams() != null ? context.queryParams().get(field) : null;
        return values == null
                ? Optional.empty()
                : values.stream().filter(StringUtils::isNotBlank).findFirst();
    }

    private static String normalizedEnvironment(final String value) {
        return StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
    }

    private static JsonObject payload(final String body) {
        if (StringUtils.isBlank(body)) {
            throw new WebhookRejectedException("PayPal webhook payload is empty.");
        }
        try {
            final JsonElement payload = JsonParser.parseString(body);
            if (!payload.isJsonObject()) {
                throw new WebhookRejectedException("PayPal webhook payload is invalid.");
            }
            return payload.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            throw new WebhookRejectedException("PayPal webhook payload is invalid.", ex);
        }
    }

    private static UUID transactionId(final JsonObject payload) {
        final JsonObject resource = requiredObject(payload, "resource");
        String transactionId = string(resource, "invoice_id");
        if (StringUtils.isBlank(transactionId)) {
            transactionId = purchaseUnitTransactionId(resource);
        }
        if (StringUtils.isBlank(transactionId)) {
            throw new WebhookRejectedException("PayPal webhook does not contain paymentTransactionId.");
        }
        try {
            return UUID.fromString(transactionId);
        } catch (IllegalArgumentException ex) {
            throw new WebhookRejectedException("PayPal webhook contains an invalid paymentTransactionId.", ex);
        }
    }

    private static String purchaseUnitTransactionId(final JsonObject resource) {
        final JsonArray purchaseUnits = array(resource, "purchase_units");
        if (purchaseUnits == null) {
            return null;
        }
        for (JsonElement element : purchaseUnits) {
            if (element.isJsonObject()) {
                final String invoiceId = string(element.getAsJsonObject(), "invoice_id");
                if (StringUtils.isNotBlank(invoiceId)) {
                    return invoiceId;
                }
            }
        }
        return null;
    }

    private static StatusDetails webhookStatusDetails(
            final String eventType,
            final PaymentTransactionStatus status) {
        final String code = switch (eventType) {
            case "PAYMENT.CAPTURE.COMPLETED", "CHECKOUT.ORDER.COMPLETED" -> COMPLETED;
            case "PAYMENT.CAPTURE.PENDING" -> PROCESSING;
            case "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.DECLINED" -> DECLINED;
            case "PAYMENT.CAPTURE.REVERSED" -> PAYMENT_FAILED;
            default -> throw new WebhookRejectedException("Unsupported PayPal webhook event: " + eventType);
        };
        return new StatusDetails(code, "PayPal webhook mapped to payment status " + status + ".");
    }

    private static PaymentTransactionStatus webhookStatus(final String eventType) {
        return switch (eventType) {
            case "PAYMENT.CAPTURE.COMPLETED", "CHECKOUT.ORDER.COMPLETED" ->
                    PaymentTransactionStatus.SUCCESS;
            case "PAYMENT.CAPTURE.PENDING" ->
                    PaymentTransactionStatus.PENDING;
            case "PAYMENT.CAPTURE.DENIED", "PAYMENT.CAPTURE.DECLINED", "PAYMENT.CAPTURE.REVERSED" ->
                    PaymentTransactionStatus.FAILED;
            default -> throw new WebhookRejectedException("Unsupported PayPal webhook event: " + eventType);
        };
    }

    private PaymentWebhookResult captureApprovedOrder(
            final PaymentWebhookContext context,
            final JsonObject payload,
            final JsonObject resource) {
        final String orderId = requiredString(resource, "id");
        final Order order = captureOrder(
                context.provider().config(),
                context.transaction().id(),
                orderId);
        final OrdersCapture capture = requireCapture(order);
        final PaymentTransactionStatus status = captureStatus(capture.getStatus());
        final Map<String, Object> data = new LinkedHashMap<>(webhookData(payload, resource));
        data.putAll(captureData(orderId, order, capture));
        return new PaymentWebhookResult(
                provider(),
                status,
                Map.copyOf(data),
                toStatusDetails(capture));
    }

    private static Map<String, Object> webhookData(
            final JsonObject payload,
            final JsonObject resource) {
        final Map<String, Object> data = new LinkedHashMap<>();
        putIfPresent(data, "eventId", string(payload, "id"));
        putIfPresent(data, "eventType", string(payload, "event_type"));
        putIfPresent(data, "resourceId", string(resource, "id"));
        putIfPresent(data, "paypalStatus", string(resource, "status"));
        putIfPresent(data, ORDER_ID, relatedOrderId(resource));
        return Map.copyOf(data);
    }

    private static String relatedOrderId(final JsonObject resource) {
        final JsonObject supplementaryData = object(resource, "supplementary_data");
        final JsonObject relatedIds = object(supplementaryData, "related_ids");
        return string(relatedIds, "order_id");
    }

    private static JsonObject requiredObject(final JsonObject source, final String name) {
        final JsonObject value = object(source, name);
        if (value == null) {
            throw new WebhookRejectedException("PayPal webhook payload is missing " + name + ".");
        }
        return value;
    }

    private static String requiredString(final JsonObject source, final String name) {
        final String value = string(source, name);
        if (StringUtils.isBlank(value)) {
            throw new WebhookRejectedException("PayPal webhook payload is missing " + name + ".");
        }
        return value;
    }

    private static JsonObject object(final JsonObject source, final String name) {
        return source != null && source.has(name) && source.get(name).isJsonObject()
                ? source.getAsJsonObject(name)
                : null;
    }

    private static JsonArray array(final JsonObject source, final String name) {
        return source != null && source.has(name) && source.get(name).isJsonArray()
                ? source.getAsJsonArray(name)
                : null;
    }

    private static String string(final JsonObject source, final String name) {
        return source != null && source.has(name) && !source.get(name).isJsonNull()
                && source.get(name).isJsonPrimitive()
                ? source.get(name).getAsString()
                : null;
    }

    private static String requireProviderConfig(final Map<String, String> config, final String name) {
        final String value = config != null ? config.get(name) : null;
        if (StringUtils.isBlank(value)) {
            throw new ProviderValidationException("PayPal provider config requires " + name + ".");
        }
        return value.trim();
    }

    private static String requiredConfig(final Map<String, String> config, final String name) {
        final String value = config != null ? config.get(name) : null;
        if (StringUtils.isBlank(value)) {
            throw new WebhookRejectedException("PayPal provider config requires " + name + ".");
        }
        return value.trim();
    }

    private static void putIfPresent(final Map<String, Object> target, final String name, final String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(name, value);
        }
    }

    private static String requireAbsoluteUri(final Map<String, Object> checkout, final String field) {
        final Object value = checkout != null ? checkout.get(field) : null;
        if (!(value instanceof String stringValue) || StringUtils.isBlank(stringValue)) {
            throw new ProviderValidationException("PayPal checkout requires " + field + ".");
        }

        final String trimmed = stringValue.trim();
        try {
            final URI uri = new URI(trimmed);
            if (!uri.isAbsolute() || StringUtils.isBlank(uri.getScheme()) || StringUtils.isBlank(uri.getHost())) {
                throw new ProviderValidationException("PayPal checkout " + field + " must be an absolute URL.");
            }
        } catch (URISyntaxException ex) {
            throw new ProviderValidationException("PayPal checkout " + field + " must be a valid URL.");
        }

        return trimmed;
    }
}
