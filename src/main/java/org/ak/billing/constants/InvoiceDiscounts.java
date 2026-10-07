package org.ak.billing.constants;

import java.math.BigDecimal;

/** Kullanıcı tipine göre yüzde indirim oranları. Faturada bunlardan yalnızca biri uygulanır (kural 7). */
public enum InvoiceDiscounts {
    CUSTOMER(new BigDecimal("0.05")),
    AFFILIATE(new BigDecimal("0.10")),
    SILVER_CART(new BigDecimal("0.20")),
    GOLD_CART(new BigDecimal("0.30"));

    private final BigDecimal discount;

    InvoiceDiscounts(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getDiscount() {
        return discount;
    }
}
