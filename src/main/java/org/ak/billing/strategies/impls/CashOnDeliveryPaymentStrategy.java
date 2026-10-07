package org.ak.billing.strategies.impls;

import org.ak.billing.helpers.Utility;
import org.ak.billing.strategies.PaymentStrategy;

import java.math.BigDecimal;

public class CashOnDeliveryPaymentStrategy implements PaymentStrategy {
    @Override
    public String pay(BigDecimal amount) {
        return "Kapıda ödeme seçildi. Kurye teslimatta " + Utility.money(amount) + " tahsil edecek.";
    }
}
