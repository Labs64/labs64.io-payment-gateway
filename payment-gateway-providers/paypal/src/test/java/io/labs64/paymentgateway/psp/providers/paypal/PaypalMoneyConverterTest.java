package io.labs64.paymentgateway.psp.providers.paypal;

import java.math.BigDecimal;

import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaypalMoneyConverterTest {

    @Test
    void convertsMinorUnitsUsingIsoCurrencyFractionDigits() {
        assertThat(PaypalMoneyConverter.toMajorUnits(BigDecimal.valueOf(3000), "USD"))
                .isEqualTo("30.00");
        assertThat(PaypalMoneyConverter.toMajorUnits(BigDecimal.valueOf(3000), "JPY"))
                .isEqualTo("3000");
        assertThat(PaypalMoneyConverter.toMajorUnits(BigDecimal.valueOf(1234), "TND"))
                .isEqualTo("1.234");
    }

    @Test
    void appliesPaypalZeroDecimalOverrides() {
        assertThat(PaypalMoneyConverter.toMajorUnits(BigDecimal.valueOf(100), "HUF"))
                .isEqualTo("100");
        assertThat(PaypalMoneyConverter.toMajorUnits(BigDecimal.valueOf(100), "TWD"))
                .isEqualTo("100");
    }

    @Test
    void normalizesIsoCurrencyCode() {
        assertThat(PaypalMoneyConverter.normalizeCurrencyCode(" jpy ")).isEqualTo("JPY");
    }

    @Test
    void rejectsUnknownAndSpecialCurrencyCodes() {
        for (final String currencyCode : new String[]{"INVALID", "XXX"}) {
            assertThatThrownBy(() -> PaypalMoneyConverter.normalizeCurrencyCode(currencyCode))
                    .as("currencyCode=%s", currencyCode)
                    .isInstanceOf(ProviderValidationException.class)
                    .hasMessage("PayPal payment requires a valid ISO-4217 purchaseOrder.currency.");
        }
    }
}
