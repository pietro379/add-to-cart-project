package org.ak.billing.strategies;

import org.ak.billing.strategies.impls.CashOnDeliveryPaymentStrategy;
import org.ak.billing.strategies.impls.CreditCardPaymentStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentStrategyTest {

    @ParameterizedTest
    @ValueSource(strings = {"4111 1111 1111 1111", "5555-5555-5555-4444", "378282246310005"})
    void acceptsValidCardNumbers(String number) {
        CreditCardPaymentStrategy card = new CreditCardPaymentStrategy(number);

        assertEquals("**** **** **** " + number.replaceAll("\\D", "").substring(number.replaceAll("\\D", "").length() - 4),
                card.getMaskedNumber());
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111 1111 1111 1112", "1234", "abcd efgh ijkl mnop", ""})
    void rejectsInvalidCardNumbers(String number) {
        assertThrows(IllegalArgumentException.class, () -> new CreditCardPaymentStrategy(number));
    }

    @Test
    void messagesShowFormattedAmount() {
        BigDecimal amount = new BigDecimal("1234.5");

        assertEquals("**** **** **** 1111 numaralı karttan $1,234.50 çekildi.",
                new CreditCardPaymentStrategy("4111111111111111").pay(amount));
        assertEquals("Kapıda ödeme seçildi. Kurye teslimatta $1,234.50 tahsil edecek.",
                new CashOnDeliveryPaymentStrategy().pay(amount));
    }
}
