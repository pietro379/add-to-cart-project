package org.ak.billing.strategies.discount;

import org.ak.billing.constants.InvoiceDiscounts;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AffiliateDiscountStrategy implements UserDiscountStrategy {
    @Override
    public BigDecimal calculateDiscount(LocalDateTime userSince, LocalDateTime now) {
        return InvoiceDiscounts.AFFILIATE.getDiscount();
    }
}
