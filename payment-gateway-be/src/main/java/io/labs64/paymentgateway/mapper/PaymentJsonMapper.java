package io.labs64.paymentgateway.mapper;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.labs64.paymentgateway.model.BillingInfo;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.model.Recurrence;
import io.labs64.paymentgateway.model.ShippingInfo;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Converts generated payment DTO fragments to JSON maps stored by JPA.
 */
@Component
@RequiredArgsConstructor
public class PaymentJsonMapper {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Named("mapToPurchaseOrder")
    public PurchaseOrder mapToPurchaseOrder(final Map<String, Object> source) {
        return toObject(source, PurchaseOrder.class);
    }

    @Named("purchaseOrderToMap")
    public Map<String, Object> purchaseOrderToMap(final PurchaseOrder source) {
        return toMap(source);
    }

    @Named("mapToBillingInfo")
    public BillingInfo mapToBillingInfo(final Map<String, Object> source) {
        return toObject(source, BillingInfo.class);
    }

    @Named("billingInfoToMap")
    public Map<String, Object> billingInfoToMap(final BillingInfo source) {
        return toMap(source);
    }

    @Named("mapToShippingInfo")
    public ShippingInfo mapToShippingInfo(final Map<String, Object> source) {
        return toObject(source, ShippingInfo.class);
    }

    @Named("shippingInfoToMap")
    public Map<String, Object> shippingInfoToMap(final ShippingInfo source) {
        return toMap(source);
    }

    @Named("mapToRecurrence")
    public Recurrence mapToRecurrence(final Map<String, Object> source) {
        return toObject(source, Recurrence.class);
    }

    @Named("recurrenceToMap")
    public Map<String, Object> recurrenceToMap(final Recurrence source) {
        return toMap(source);
    }

    private <T> T toObject(final Map<String, Object> source, final Class<T> targetType) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(source, targetType);
    }

    private Map<String, Object> toMap(final Object source) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(source, MAP_TYPE);
    }
}
