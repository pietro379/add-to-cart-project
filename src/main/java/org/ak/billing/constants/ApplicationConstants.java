package org.ak.billing.constants;

import java.math.BigDecimal;

public enum ApplicationConstants {
    CART_QUANTITY(2),
    SHOW_LOGS(true),
    INVENTORY_SHORTAGE_EX_MSG("Stok yetersiz, ürün sepete eklenemiyor."),
    DATE_TIME_FORMAT("dd MMMM yyyy, HH:mm"),
    BILL_HEADER(" FATURA "),
    BILL_LENGTH(72),
    LOYALTY_YEARS(2),
    LOW_STOCK_THRESHOLD(5),
    EXTRA_DISCOUNT_THRESHOLD(new BigDecimal("200.00")),
    EXTRA_DISCOUNT_AMOUNT(new BigDecimal("5.00"));

    private final Object appCons;

    ApplicationConstants(Object appCons) {
        this.appCons = appCons;
    }

    public final Object getApplicationConstant() {
        return appCons;
    }

    public final int asInt() {
        return (int) appCons;
    }

    public final BigDecimal asDecimal() {
        return (BigDecimal) appCons;
    }
}
