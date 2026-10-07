package org.ak.billing.strategies.discount;

import org.ak.billing.constants.InvoiceDiscounts;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SilverCartDiscountStrategy implements UserDiscountStrategy {
    @Override
    public BigDecimal calculateDiscount(LocalDateTime userSince, LocalDateTime now) {
        return InvoiceDiscounts.SILVER_CART.getDiscount();
    }
}
