package org.ak.billing.strategies;

import java.math.BigDecimal;

public interface PaymentStrategy {
    /** Ödemeyi alır ve kullanıcıya gösterilecek onay mesajını döner. */
    String pay(BigDecimal amount);
}
