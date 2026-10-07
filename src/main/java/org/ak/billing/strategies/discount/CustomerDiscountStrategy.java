package org.ak.billing.strategies.discount;

import org.ak.billing.constants.ApplicationConstants;
import org.ak.billing.constants.InvoiceDiscounts;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerDiscountStrategy implements UserDiscountStrategy {
    /**
     * Kural 4: "2 yıldan uzun süredir müşteri". 2 yıl 1 gün de sayılır;
     * {@code ChronoUnit.YEARS.between(...) > 2} ise 3 tam yıl isterdi.
     */
    @Override
    public BigDecimal calculateDiscount(LocalDateTime userSince, LocalDateTime now) {
        if (userSince == null) {
            return BigDecimal.ZERO;
        }
        LocalDateTime loyaltyStart = userSince.plusYears(ApplicationConstants.LOYALTY_YEARS.asInt());
        return loyaltyStart.isBefore(now) ? InvoiceDiscounts.CUSTOMER.getDiscount() : BigDecimal.ZERO;
    }
}
