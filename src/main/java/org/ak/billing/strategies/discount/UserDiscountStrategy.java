package org.ak.billing.strategies.discount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface UserDiscountStrategy {
    /** Kullanıcıya uygulanacak yüzde indirim oranı (0.30 = %30). */
    BigDecimal calculateDiscount(LocalDateTime userSince, LocalDateTime now);

    default BigDecimal calculateDiscount(LocalDateTime userSince) {
        return calculateDiscount(userSince, LocalDateTime.now());
    }
}
