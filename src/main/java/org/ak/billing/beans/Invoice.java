package org.ak.billing.beans;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Değiştirilemez fatura. Tutarın nasıl oluştuğunu göstermek için indirim dökümünü de taşır:
 * {@code amount = subtotal - userDiscount - billDiscount}.
 */
public final class Invoice {
    private final UUID uid;
    private final LocalDateTime date;
    private final BigDecimal subtotal;
    private final BigDecimal userDiscountRate;
    private final BigDecimal userDiscount;
    private final BigDecimal billDiscount;
    private final BigDecimal amount;

    public Invoice(UUID uid, LocalDateTime date, BigDecimal subtotal, BigDecimal userDiscountRate,
            BigDecimal userDiscount, BigDecimal billDiscount) {
        this.uid = Objects.requireNonNull(uid);
        this.date = Objects.requireNonNull(date);
        this.subtotal = subtotal;
        this.userDiscountRate = userDiscountRate;
        this.userDiscount = userDiscount;
        this.billDiscount = billDiscount;
        this.amount = subtotal.subtract(userDiscount).subtract(billDiscount);
    }

    /** Ödenecek net tutar. */
    public BigDecimal getAmount() {
        return amount;
    }

    /** Uygulanan toplam indirim (kullanıcı yüzdesi + her 200$ için 5$). */
    public BigDecimal getDiscount() {
        return userDiscount.add(billDiscount);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getUserDiscountRate() {
        return userDiscountRate;
    }

    public BigDecimal getUserDiscount() {
        return userDiscount;
    }

    public BigDecimal getBillDiscount() {
        return billDiscount;
    }

    public UUID getUid() {
        return uid;
    }

    public LocalDateTime getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "Invoice{" +
                "uid='" + uid + '\'' +
                ", date=" + date +
                ", subtotal=" + subtotal +
                ", userDiscount=" + userDiscount +
                ", billDiscount=" + billDiscount +
                ", amount=" + amount +
                '}';
    }
}
