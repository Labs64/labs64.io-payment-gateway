package io.labs64.paymentgateway.service.filter;

import org.apache.commons.lang3.StringUtils;

public record PaymentProviderFilter(String currency, String country, Boolean active) {
    public boolean isCountryBlank() {
        return StringUtils.isBlank(country);
    }

    public boolean isCurrencyBlank() {
        return StringUtils.isBlank(currency);
    }
}
