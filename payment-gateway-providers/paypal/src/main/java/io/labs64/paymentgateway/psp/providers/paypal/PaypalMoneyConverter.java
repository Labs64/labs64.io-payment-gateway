package io.labs64.paymentgateway.psp.providers.paypal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

import io.labs64.paymentgateway.psp.spi.ProviderValidationException;

/**
 * Converts gateway minor-unit amounts to the decimal representation required by PayPal.
 */
final class PaypalMoneyConverter {

    // PayPal requires whole-unit amounts for these currencies despite their ISO fraction digits.
    // https://developer.paypal.com/api/rest/reference/currency-codes/
    private static final Map<String, Integer> PAYPAL_FRACTION_DIGIT_OVERRIDES = Map.of(
            "HUF", 0,
            "TWD", 0);

    private PaypalMoneyConverter() {
    }

    static String normalizeCurrencyCode(final String currencyCode) {
        final String normalizedCurrencyCode = currencyCode.trim().toUpperCase(Locale.ROOT);
        fractionDigits(normalizedCurrencyCode);
        return normalizedCurrencyCode;
    }

    static String toMajorUnits(final BigDecimal minorUnits, final String currencyCode) {
        final int fractionDigits = fractionDigits(currencyCode);
        return minorUnits.movePointLeft(fractionDigits)
                .setScale(fractionDigits, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private static int fractionDigits(final String currencyCode) {
        final Integer override = PAYPAL_FRACTION_DIGIT_OVERRIDES.get(currencyCode);
        if (override != null) {
            return override;
        }

        final Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException ex) {
            throw invalidCurrency(ex);
        }

        final int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw invalidCurrency(null);
        }
        return fractionDigits;
    }

    private static ProviderValidationException invalidCurrency(final Exception cause) {
        final String message = "PayPal payment requires a valid ISO-4217 purchaseOrder.currency.";
        return cause == null
                ? new ProviderValidationException(message)
                : new ProviderValidationException(message, cause);
    }
}
