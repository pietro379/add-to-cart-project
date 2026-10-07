package org.ak.billing.helpers;

import org.ak.billing.beans.Invoice;
import org.ak.billing.beans.Product;
import org.ak.billing.beans.Shopper;
import org.ak.billing.beans.UserDetails;
import org.ak.billing.constants.ApplicationConstants;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Para, tarih ve fatura biçimlendirme yardımcıları. */
public final class Utility {
    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final int WIDTH = ApplicationConstants.BILL_LENGTH.asInt();
    private static final String ROW = "%-24s %-12s %5s %13s %13s";

    private Utility() {
    }

    /** {@code 1234.5 -> "$1,234.50"}. Fiyatlar dolar cinsinden olduğu için ABD biçimi kullanılır. */
    public static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** {@code 0.30 -> "%30"}. */
    public static String percent(BigDecimal rate) {
        return "%" + rate.movePointRight(2).stripTrailingZeros().toPlainString();
    }

    public static String getFormattedDate(LocalDateTime localDateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                (String) ApplicationConstants.DATE_TIME_FORMAT.getApplicationConstant(), TR);
        return localDateTime.format(formatter);
    }

    public static String getCSVFromList(List<String> list) {
        return list == null ? "" : String.join(", ", list);
    }

    public static String line(char c) {
        return StringUtils.repeat(c, WIDTH);
    }

    public static String center(String text) {
        return StringUtils.center(text, WIDTH);
    }

    public static String truncate(String text, int max) {
        return StringUtils.abbreviate(text, max);
    }

    /** Ürün tablosunun satırları (başlık dahil). Sepet görünümü ve fatura aynı düzeni kullanır. */
    public static String renderProducts(Collection<Product> products) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(ROW, "Ürün", "Tür", "Adet", "Birim fiyat", "Tutar")).append('\n');
        sb.append(line('-')).append('\n');
        for (Product p : products) {
            sb.append(String.format(ROW, truncate(p.getName(), 24), p.getType(), p.getQuantity(),
                    money(p.getUnitPrice()), money(p.getLineTotal()))).append('\n');
        }
        return sb.toString();
    }

    public static String renderInvoice(Shopper shopper) {
        Invoice invoice = shopper.getInvoice();
        UserDetails user = shopper.getUserDetails();
        StringBuilder sb = new StringBuilder();

        sb.append(line('=')).append('\n');
        sb.append(center((String) ApplicationConstants.BILL_HEADER.getApplicationConstant())).append('\n');
        sb.append(line('=')).append('\n');
        meta(sb, "Fatura no", invoice.getUid().toString());
        meta(sb, "Tarih", getFormattedDate(invoice.getDate()));
        meta(sb, "Müşteri", (user.getName() == null ? user.getUid() : user.getName()) + " (" + user.getUserType() + ")");
        if (user.getUserSince() != null) {
            meta(sb, "Üyelik", getFormattedDate(user.getUserSince()));
        }
        if (user.getContacts() != null && !user.getContacts().isEmpty()) {
            meta(sb, "İletişim", getCSVFromList(user.getContacts()));
        }
        sb.append(line('-')).append('\n');
        sb.append(renderProducts(shopper.getShoppingCart().getProductsInCart().getProducts().values()));
        sb.append(line('-')).append('\n');

        total(sb, "Ara toplam", money(invoice.getSubtotal()));
        if (invoice.getUserDiscount().signum() > 0) {
            total(sb, "Üye indirimi (" + percent(invoice.getUserDiscountRate()) + ", telefon hariç)",
                    "-" + money(invoice.getUserDiscount()));
        }
        if (invoice.getBillDiscount().signum() > 0) {
            total(sb, "Her $200 için $5 indirim", "-" + money(invoice.getBillDiscount()));
        }
        sb.append(line('=')).append('\n');
        total(sb, "ÖDENECEK TUTAR", money(invoice.getAmount()));
        sb.append(line('=')).append('\n');
        return sb.toString();
    }

    private static void meta(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-12s: %s%n", label, value));
    }

    private static void total(StringBuilder sb, String label, String value) {
        sb.append(String.format("%" + (WIDTH - 15) + "s %14s%n", label, value));
    }
}
