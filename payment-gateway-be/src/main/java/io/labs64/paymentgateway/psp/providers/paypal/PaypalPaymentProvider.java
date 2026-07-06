package io.labs64.paymentgateway.psp.providers.paypal;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.Address;
import com.paypal.sdk.models.AmountBreakdown;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CaptureOrderInput;
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
import com.paypal.sdk.models.OrderStatus;
import com.paypal.sdk.models.Payer;
import com.paypal.sdk.models.PhoneNumber;
import com.paypal.sdk.models.PhoneType;
import com.paypal.sdk.models.PhoneWithType;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.paypal.sdk.models.ShippingDetails;
import com.paypal.sdk.models.ShippingName;
import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.exception.PspException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.model.NextAction;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.CheckoutSessionDraft;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutContext;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutSupport;
import io.labs64.paymentgateway.psp.spi.ProviderConfigField;
import io.labs64.paymentgateway.psp.spi.ProviderConfigSupport;
import io.labs64.paymentgateway.psp.spi.StatusDetails;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

/**
 * PayPal payment provider.
 * <p>
 * This class currently owns the PayPal tenant configuration contract and
 * browser checkout order create/capture flow.
 */
@Component
public class PaypalPaymentProvider implements PaymentProvider, ProviderConfigSupport, ProviderCheckoutSupport {

    public static final String PROVIDER = "paypal";

    // configuration
    private static final String CLIENT_ID = "clientId";
    private static final String CLIENT_SECRET = "clientSecret";
    private static final String ENVIRONMENT = "environment";

    // environment
    private static final String SANDBOX = "sandbox";
    private static final String LIVE = "live";

    // checkout fields
    private static final String RETURN_URL = "returnUrl";
    private static final String CANCEL_URL = "cancelUrl";
    private static final String ORDER_ID = "orderId";
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
            ProviderConfigField.required(ENVIRONMENT));

    private final PaymentGatewayProperties properties;

    public PaypalPaymentProvider(final PaymentGatewayProperties properties) {
        this.properties = properties;
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
            throw new ValidationException("PayPal environment must be either sandbox or live.");
        }
    }

    @Override
    public Optional<CheckoutSessionDraft> prepareCheckoutSession(final CheckoutPreparationContext context) {
        final Map<String, Object> checkout = context.request().checkout();
        final String returnUrl = requireAbsoluteUri(checkout, RETURN_URL);
        final String cancelUrl = requireAbsoluteUri(checkout, CANCEL_URL);

        return Optional.of(new CheckoutSessionDraft(Map.of(RETURN_URL, returnUrl, CANCEL_URL, cancelUrl), null));
    }

    @Override
    public PaymentResult execute(final PaymentContext context) {
        if (context.checkoutSession() == null) {
            throw new ValidationException("PayPal checkout session is required.");
        }

        final Order order = createOrder(context);
        final String orderId = requireOrderId(order);
        final String approveUrl = requireApproveUrl(order);

        return new PaymentResult(
                provider(),
                PaymentTransactionStatus.PENDING,
                Map.of(ORDER_ID, orderId, "status", status(order)),
                new StatusDetails("PENDING", "PayPal order is waiting for buyer approval."),
                new PaymentNextAction(NextAction.TypeEnum.REDIRECT, Map.of("url", approveUrl)));
    }

    @Override
    public PaymentResult completeCheckout(final ProviderCheckoutContext context) {
        final String orderId = requireQueryParam(context, "token");
        final Order order = captureOrder(context, orderId);
        final PaymentTransactionStatus status = OrderStatus.COMPLETED.equals(order != null ? order.getStatus() : null)
                ? PaymentTransactionStatus.SUCCESS
                : PaymentTransactionStatus.FAILED;

        return new PaymentResult(
                provider(),
                status,
                Map.of(ORDER_ID, orderId, "status", status(order)),
                toStatusDetails(order),
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
                new StatusDetails("CANCELLED", "PayPal checkout was cancelled by the buyer."),
                redirectToSessionPayload(context, CANCEL_URL));
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
            throw new PspException("PayPal order creation failed.", ex);
        }
    }

    private Order captureOrder(final ProviderCheckoutContext context, final String orderId) {
        final CaptureOrderInput input = new CaptureOrderInput.Builder(orderId, CONTENT_TYPE_JSON)
                .prefer(PREFER_REPRESENTATION)
                .paypalRequestId(context.transaction().id().toString())
                .body(new OrderCaptureRequest.Builder().build())
                .build();

        try {
            final ApiResponse<Order> response = client(context.provider().config())
                    .getOrdersController()
                    .captureOrder(input);
            return response.getResult();
        } catch (com.paypal.sdk.exceptions.ApiException | IOException ex) {
            throw new PspException("PayPal order capture failed.", ex);
        }
    }

    private OrderRequest toOrderRequest(final PaymentContext context) {
        final OrderRequest.Builder builder = new OrderRequest.Builder(
                CheckoutPaymentIntent.CAPTURE,
                List.of(toPurchaseUnit(context)))
                .applicationContext(toApplicationContext(
                        context.checkoutSession().id(),
                        hasShippingInfo(context.payment().shippingInfo())))
                .payer(toPayer(context.payment().billingInfo()));
        return builder.build();
    }

    private PurchaseUnitRequest toPurchaseUnit(final PaymentContext context) {
        final Map<String, Object> purchaseOrder = context.payment().purchaseOrder();
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

    private AmountWithBreakdown toAmount(final Map<String, Object> purchaseOrder, final List<ItemRequest> items) {
        final String currency = requireString(purchaseOrder, "currency");
        final BigDecimal grossAmount = requireAmount(purchaseOrder, "grossAmount");
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
            final Map<String, Object> purchaseOrder,
            final String currency,
            final List<ItemRequest> items) {
        if (items.isEmpty()) {
            return null;
        }

        final BigDecimal itemTotal = items.stream()
                .map(item -> fromMajorUnits(item.getUnitAmount().getValue())
                        .multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal taxTotal = optionalAmount(purchaseOrder, "taxAmount").orElse(BigDecimal.ZERO);
        final BigDecimal grossAmount = requireAmount(purchaseOrder, "grossAmount");
        if (itemTotal.add(taxTotal).compareTo(grossAmount) != 0) {
            throw new ValidationException("PayPal payment requires purchaseOrder items and taxAmount to match grossAmount.");
        }

        final AmountBreakdown.Builder builder = new AmountBreakdown.Builder()
                .itemTotal(toMoney(currency, itemTotal));
        if (taxTotal.compareTo(BigDecimal.ZERO) > 0) {
            builder.taxTotal(toMoney(currency, taxTotal));
        }
        return builder.build();
    }

    private List<ItemRequest> toItems(final Map<String, Object> purchaseOrder, final boolean physicalGoods) {
        final Object source = purchaseOrder != null ? purchaseOrder.get("items") : null;
        if (!(source instanceof List<?> rawItems) || rawItems.isEmpty()) {
            return List.of();
        }

        return rawItems.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> toItem(item, requireString(purchaseOrder, "currency"), physicalGoods))
                .toList();
    }

    private ItemRequest toItem(
            final Map<?, ?> item,
            final String currency,
            final boolean physicalGoods) {
        final String name = limit(
                requireString(item, "name", "purchaseOrder.items[].name"),
                PAYPAL_MAX_ITEM_NAME_LENGTH);
        final ItemRequest.Builder builder = new ItemRequest.Builder(
                name,
                toMoney(currency, requireAmount(item, "price", "purchaseOrder.items[].price")),
                String.valueOf(requireInteger(item, "quantity", "purchaseOrder.items[].quantity")))
                .category(physicalGoods ? ItemCategory.PHYSICAL_GOODS : ItemCategory.DIGITAL_GOODS);

        optionalString(item, "description").ifPresent(value ->
                builder.description(limit(value, PAYPAL_MAX_ITEM_DESCRIPTION_LENGTH)));
        optionalString(item, "sku").ifPresent(value -> builder.sku(limit(value, PAYPAL_MAX_SKU_LENGTH)));
        optionalString(item, "url").ifPresent(builder::url);
        optionalString(item, "image").ifPresent(builder::imageUrl);

        return builder.build();
    }

    private Payer toPayer(final Map<String, Object> billingInfo) {
        if (billingInfo == null || billingInfo.isEmpty()) {
            return null;
        }

        final Payer.Builder builder = new Payer.Builder();
        optionalString(billingInfo, "email").ifPresent(builder::emailAddress);
        toName(billingInfo).ifPresent(builder::name);
        toPayerPhone(billingInfo).ifPresent(builder::phone);
        toAddress(billingInfo).ifPresent(builder::address);
        return builder.build();
    }

    private ShippingDetails toShipping(final Map<String, Object> shippingInfo) {
        if (!hasShippingInfo(shippingInfo)) {
            return null;
        }

        final ShippingDetails.Builder builder = new ShippingDetails.Builder()
                .type(FulfillmentType.SHIPPING);
        toShippingName(shippingInfo).ifPresent(builder::name);
        optionalString(shippingInfo, "email").ifPresent(builder::emailAddress);
        toAddress(shippingInfo).ifPresent(builder::address);
        return builder.build();
    }

    private static Optional<Name> toName(final Map<String, Object> source) {
        final Optional<String> firstName = optionalString(source, "firstName");
        final Optional<String> lastName = optionalString(source, "lastName");
        if (firstName.isEmpty() && lastName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Name.Builder()
                .givenName(firstName.orElse(null))
                .surname(lastName.orElse(null))
                .build());
    }

    private static Optional<ShippingName> toShippingName(final Map<String, Object> source) {
        final String fullName = Stream.of(
                        optionalString(source, "firstName").orElse(""),
                        optionalString(source, "lastName").orElse(""))
                .filter(StringUtils::isNotBlank)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        return StringUtils.isBlank(fullName)
                ? Optional.empty()
                : Optional.of(new ShippingName.Builder().fullName(fullName).build());
    }

    private static Optional<Address> toAddress(final Map<String, Object> source) {
        final Optional<String> country = optionalString(source, "country");
        if (country.isEmpty()) {
            return Optional.empty();
        }

        final Address.Builder builder = new Address.Builder(country.get());
        optionalString(source, "address1").ifPresent(builder::addressLine1);
        optionalString(source, "address2").ifPresent(builder::addressLine2);
        optionalString(source, "city").ifPresent(builder::adminArea2);
        optionalString(source, "state").ifPresent(builder::adminArea1);
        optionalString(source, "postalCode").ifPresent(builder::postalCode);
        return Optional.of(builder.build());
    }

    private static Optional<PhoneWithType> toPayerPhone(final Map<String, Object> source) {
        return optionalString(source, "phone")
                .map(phone -> new PhoneWithType.Builder(new PhoneNumber.Builder(phone).build())
                        .phoneType(PhoneType.MOBILE)
                        .build());
    }

    private static boolean hasShippingInfo(final Map<String, Object> shippingInfo) {
        return shippingInfo != null && !shippingInfo.isEmpty();
    }

    private OrderApplicationContext toApplicationContext(
            final UUID checkoutSessionId,
            final boolean hasShippingInfo) {
        return new OrderApplicationContext.Builder()
                .returnUrl(providerCheckoutUrl(checkoutSessionId, "return"))
                .cancelUrl(providerCheckoutUrl(checkoutSessionId, "cancel"))
                .shippingPreference(hasShippingInfo
                        ? OrderApplicationContextShippingPreference.SET_PROVIDED_ADDRESS
                        : OrderApplicationContextShippingPreference.NO_SHIPPING)
                .userAction(OrderApplicationContextUserAction.PAY_NOW)
                .build();
    }

    private String providerCheckoutUrl(final UUID checkoutSessionId, final String action) {
        return stripTrailingSlash(properties.getPublicBaseUrl())
                + "/providers/" + PROVIDER + "/checkout-sessions/" + checkoutSessionId + "/" + action;
    }

    private PaypalServerSdkClient client(final Map<String, String> config) {
        return new PaypalServerSdkClient.Builder()
                .environment(toEnvironment(config.get(ENVIRONMENT)))
                .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(
                        config.get(CLIENT_ID),
                        config.get(CLIENT_SECRET))
                        .build())
                .build();
    }

    private static Environment toEnvironment(final String value) {
        return LIVE.equals(normalizedEnvironment(value)) ? Environment.PRODUCTION : Environment.SANDBOX;
    }

    private static String requireOrderId(final Order order) {
        if (order == null || StringUtils.isBlank(order.getId())) {
            throw new PspException("PayPal order creation returned no order id.");
        }
        return order.getId();
    }

    private static String requireApproveUrl(final Order order) {
        if (order == null || order.getLinks() == null) {
            throw new PspException("PayPal order creation returned no approval link.");
        }
        return order.getLinks().stream()
                .filter(link -> APPROVE_REL.equalsIgnoreCase(link.getRel()))
                .map(LinkDescription::getHref)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElseThrow(() -> new PspException("PayPal order creation returned no approval link."));
    }

    private static StatusDetails toStatusDetails(final Order order) {
        final String status = status(order);
        if (OrderStatus.COMPLETED.equals(order != null ? order.getStatus() : null)) {
            return new StatusDetails("SUCCESS", "PayPal order was captured successfully.");
        }
        return new StatusDetails(status, "PayPal order was not completed.");
    }

    private static PaymentNextAction redirectToSessionPayload(
            final ProviderCheckoutContext context,
            final String field) {
        final Map<String, Object> payload = context.checkoutSession() != null
                ? context.checkoutSession().payload()
                : null;
        final Object url = payload != null ? payload.get(field) : null;
        if (!(url instanceof String stringUrl) || StringUtils.isBlank(stringUrl)) {
            throw new ValidationException("PayPal checkout session payload requires " + field + ".");
        }
        return new PaymentNextAction(NextAction.TypeEnum.REDIRECT, Map.of("url", stringUrl.trim()));
    }

    private static String status(final Order order) {
        return order != null && order.getStatus() != null ? order.getStatus().toString() : "UNKNOWN";
    }

    private static String requireString(final Map<String, Object> source, final String field) {
        final Object value = source != null ? source.get(field) : null;
        if (!(value instanceof String stringValue) || StringUtils.isBlank(stringValue)) {
            throw new ValidationException("PayPal payment requires purchaseOrder." + field + ".");
        }
        return stringValue.trim();
    }

    private static String requireString(final Map<?, ?> source, final String field, final String path) {
        final Object value = source != null ? source.get(field) : null;
        if (!(value instanceof String stringValue) || StringUtils.isBlank(stringValue)) {
            throw new ValidationException("PayPal payment requires " + path + ".");
        }
        return stringValue.trim();
    }

    private static Optional<String> optionalString(final Map<?, ?> source, final String field) {
        final Object value = source != null ? source.get(field) : null;
        return value instanceof String stringValue && StringUtils.isNotBlank(stringValue)
                ? Optional.of(stringValue.trim())
                : Optional.empty();
    }

    private static BigDecimal requireAmount(final Map<String, Object> source, final String field) {
        final Object value = source != null ? source.get(field) : null;
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String stringValue && StringUtils.isNotBlank(stringValue)) {
            return new BigDecimal(stringValue.trim());
        }
        throw new ValidationException("PayPal payment requires purchaseOrder." + field + ".");
    }

    private static BigDecimal requireAmount(final Map<?, ?> source, final String field, final String path) {
        final Object value = source != null ? source.get(field) : null;
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String stringValue && StringUtils.isNotBlank(stringValue)) {
            return new BigDecimal(stringValue.trim());
        }
        throw new ValidationException("PayPal payment requires " + path + ".");
    }

    private static Optional<BigDecimal> optionalAmount(final Map<String, Object> source, final String field) {
        final Object value = source != null ? source.get(field) : null;
        if (value instanceof Number number) {
            return Optional.of(new BigDecimal(number.toString()));
        }
        if (value instanceof String stringValue && StringUtils.isNotBlank(stringValue)) {
            return Optional.of(new BigDecimal(stringValue.trim()));
        }
        return Optional.empty();
    }

    private static int requireInteger(final Map<?, ?> source, final String field, final String path) {
        final Object value = source != null ? source.get(field) : null;

        try {
            final int integerValue;
            if (value instanceof Number number) {
                integerValue = new BigDecimal(number.toString()).intValueExact();
            } else if (value instanceof String stringValue && StringUtils.isNotBlank(stringValue)) {
                integerValue = Integer.parseInt(stringValue.trim());
            } else {
                integerValue = 0;
            }

            if (integerValue > 0) {
                return integerValue;
            }
        } catch (ArithmeticException | NumberFormatException ignored) {
        }

        throw new ValidationException("PayPal payment requires " + path + ".");
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
                .orElseThrow(() -> new ValidationException("PayPal checkout callback requires query parameter: " + field));
    }

    private static Optional<String> firstQueryParam(final ProviderCheckoutContext context, final String field) {
        final List<String> values = context.queryParams() != null ? context.queryParams().get(field) : null;
        return values == null
                ? Optional.empty()
                : values.stream().filter(StringUtils::isNotBlank).findFirst();
    }

    private static String stripTrailingSlash(final String source) {
        return Strings.CS.removeEnd(StringUtils.defaultString(source), "/");
    }

    private static String normalizedEnvironment(final String value) {
        return StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String requireAbsoluteUri(final Map<String, Object> checkout, final String field) {
        final Object value = checkout != null ? checkout.get(field) : null;
        if (!(value instanceof String stringValue) || StringUtils.isBlank(stringValue)) {
            throw new ValidationException("PayPal checkout requires " + field + ".");
        }

        final String trimmed = stringValue.trim();
        try {
            final URI uri = new URI(trimmed);
            if (!uri.isAbsolute() || StringUtils.isBlank(uri.getScheme()) || StringUtils.isBlank(uri.getHost())) {
                throw new ValidationException("PayPal checkout " + field + " must be an absolute URL.");
            }
        } catch (URISyntaxException ex) {
            throw new ValidationException("PayPal checkout " + field + " must be a valid URL.");
        }

        return trimmed;
    }
}
