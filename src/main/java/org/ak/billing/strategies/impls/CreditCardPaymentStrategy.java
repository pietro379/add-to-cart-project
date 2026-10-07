package org.ak.billing.strategies.impls;

import org.ak.billing.helpers.Utility;
import org.ak.billing.strategies.PaymentStrategy;

import java.math.BigDecimal;

public class CreditCardPaymentStrategy implements PaymentStrategy {
    private final String cardNumber;

    /**
     * @param cardNumber boşluk veya tire içerebilir; 12-19 haneli ve Luhn kontrolünden geçmeli
     * @throws IllegalArgumentException kart numarası geçersizse
     */
    public CreditCardPaymentStrategy(String cardNumber) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
        if (!isValid(digits)) {
            throw new IllegalArgumentException("Geçersiz kart numarası.");
        }
        this.cardNumber = digits;
    }

    /** Luhn (mod 10) kontrolü: kart numarasındaki yazım hatalarını yakalar. */
    public static boolean isValid(String digits) {
        if (!digits.matches("\\d{12,19}")) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    public String getMaskedNumber() {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    @Override
    public String pay(BigDecimal amount) {
        return getMaskedNumber() + " numaralı karttan " + Utility.money(amount) + " çekildi.";
    }
}
