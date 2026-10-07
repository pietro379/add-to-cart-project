package org.ak.billing.strategies.impls;

import org.ak.billing.beans.Invoice;
import org.ak.billing.beans.Product;
import org.ak.billing.beans.Shopper;
import org.ak.billing.beans.UserDetails;
import org.ak.billing.constants.ApplicationConstants;
import org.ak.billing.strategies.InvoicingStrategy;
import org.ak.billing.strategies.discount.UserDiscountFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Problem tanımındaki indirim kurallarını uygular:
 * <ol>
 * <li>Kullanıcı tipine göre tek bir yüzde indirim seçilir (Gold %30, Silver %20, Affiliate %10,
 * 2 yıldan eski müşteri %5).</li>
 * <li>Yüzde indirim telefonlara uygulanmaz.</li>
 * <li>Yüzde indirimden sonra kalan tutardaki her 200$ için 5$ indirim yapılır.</li>
 * </ol>
 */
public class MyInvoiceGenerator implements InvoicingStrategy {
    private static final int MONEY_SCALE = 2;

    private final Clock clock;

    public MyInvoiceGenerator() {
        this(Clock.systemDefaultZone());
    }

    /** Testlerde "bugün"ü sabitlemek için saat dışarıdan verilebilir. */
    public MyInvoiceGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void generate(Shopper shopper) {
        UserDetails user = shopper.getUserDetails();
        Collection<Product> products = shopper.getShoppingCart().getProductsInCart().getProducts().values();
        LocalDateTime now = LocalDateTime.now(clock);

        BigDecimal subtotal = sum(products, p -> true);
        BigDecimal discountable = sum(products, p -> !p.isPhone());

        BigDecimal rate = UserDiscountFactory.getStrategy(user.getUserType())
                .calculateDiscount(user.getUserSince(), now);
        BigDecimal userDiscount = money(discountable.multiply(rate));

        BigDecimal afterUserDiscount = subtotal.subtract(userDiscount);
        BigDecimal billDiscount = billDiscount(afterUserDiscount);

        shopper.setInvoice(new Invoice(UUID.randomUUID(), now, money(subtotal), rate, userDiscount, billDiscount));
    }

    /** Kural 5: her tam 200$ için 5$. Örn. 950$ için 4 x 5 = 20$. */
    static BigDecimal billDiscount(BigDecimal amount) {
        BigDecimal threshold = ApplicationConstants.EXTRA_DISCOUNT_THRESHOLD.asDecimal();
        BigDecimal perThreshold = ApplicationConstants.EXTRA_DISCOUNT_AMOUNT.asDecimal();
        BigDecimal times = amount.divideToIntegralValue(threshold);
        return money(perThreshold.multiply(times));
    }

    private static BigDecimal sum(Collection<Product> products, Predicate<Product> filter) {
        return products.stream()
                .filter(filter)
                .map(Product::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
