package org.ak.billing.strategies;

import org.ak.billing.beans.Invoice;
import org.ak.billing.beans.Product;
import org.ak.billing.beans.Shopper;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.beans.UserDetails;
import org.ak.billing.constants.ProductTypes;
import org.ak.billing.constants.UserTypes;
import org.ak.billing.strategies.impls.MyInvoiceGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MyInvoiceGeneratorTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);
    private final MyInvoiceGenerator generator =
            new MyInvoiceGenerator(Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private static Product product(ProductTypes type, String price, int quantity) {
        return new Product(UUID.randomUUID(), type.name(), quantity, type, new BigDecimal(price));
    }

    private Invoice invoice(UserTypes type, LocalDateTime since, Product... products) {
        ShoppingCart cart = new ShoppingCart();
        for (Product p : products) {
            cart.getProductsInCart().getProducts().put(p.getId(), p);
        }
        UserDetails user = new UserDetails.Builder().userType(type).userSince(since).build();
        Shopper shopper = new Shopper(user, cart);
        generator.generate(shopper);
        return shopper.getInvoice();
    }

    @Test
    @DisplayName("Kural 1 ve 6: Gold %30, telefonlara uygulanmaz")
    void goldDiscountSkipsPhones() {
        Invoice invoice = invoice(UserTypes.GOLD_CART, NOW,
                product(ProductTypes.PHONE, "100.00", 2),
                product(ProductTypes.CLOTHING, "50.00", 1));

        assertEquals(new BigDecimal("250.00"), invoice.getSubtotal());
        assertEquals(new BigDecimal("15.00"), invoice.getUserDiscount()); // yalnızca 50 x %30
        assertEquals(new BigDecimal("5.00"), invoice.getBillDiscount());  // kalan 235 -> 1 x 200
        assertEquals(new BigDecimal("230.00"), invoice.getAmount());
    }

    @Test
    @DisplayName("Kural 5: her $200 için $5 (problem tanımındaki $950 -> $20 örneği)")
    void fiveDollarsPerTwoHundred() {
        Invoice invoice = invoice(UserTypes.CUSTOMER, NOW, product(ProductTypes.ELECTRONICS, "950.00", 1));

        assertEquals(new BigDecimal("20.00"), invoice.getBillDiscount());
        assertEquals(new BigDecimal("930.00"), invoice.getAmount());
    }

    @Test
    @DisplayName("Kural 5 yüzde indirimden sonra kalan tutara uygulanır")
    void billDiscountAfterPercentage() {
        // 1000 elektronik (Gold -> 700) + 500 telefon = 1200 -> 6 x $5
        Invoice invoice = invoice(UserTypes.GOLD_CART, NOW,
                product(ProductTypes.ELECTRONICS, "1000.00", 1),
                product(ProductTypes.PHONE, "500.00", 1));

        assertEquals(new BigDecimal("300.00"), invoice.getUserDiscount());
        assertEquals(new BigDecimal("30.00"), invoice.getBillDiscount());
        assertEquals(new BigDecimal("1170.00"), invoice.getAmount());
    }

    @Test
    @DisplayName("Kural 7: tek yüzde indirim; uzun süreli Gold üye %35 değil %30 alır")
    void onlyOnePercentageDiscount() {
        Invoice invoice = invoice(UserTypes.GOLD_CART, NOW.minusYears(5), product(ProductTypes.CLOTHING, "100.00", 1));

        assertEquals(new BigDecimal("0.30"), invoice.getUserDiscountRate());
        assertEquals(new BigDecimal("70.00"), invoice.getAmount());
    }

    @Test
    @DisplayName("Kural 4: 2 yıl 1 gün müşteri %5 alır, 2 yıldan az olan almaz")
    void loyaltyBoundary() {
        Product item = product(ProductTypes.COSMETICS, "100.00", 1);

        assertEquals(new BigDecimal("95.00"),
                invoice(UserTypes.CUSTOMER, NOW.minusYears(2).minusDays(1), item).getAmount());
        assertEquals(new BigDecimal("100.00"),
                invoice(UserTypes.CUSTOMER, NOW.minusYears(2).plusDays(1), item).getAmount());
    }

    @Test
    @DisplayName("Tutarlar kuruş hassasiyetinde yuvarlanır")
    void roundsToCents() {
        Invoice invoice = invoice(UserTypes.AFFILIATE, NOW, product(ProductTypes.STATIONERY, "1.99", 3));

        assertEquals(new BigDecimal("5.97"), invoice.getSubtotal());
        assertEquals(new BigDecimal("0.60"), invoice.getUserDiscount()); // 0.597 -> 0.60
        assertEquals(new BigDecimal("5.37"), invoice.getAmount());
    }

    @Test
    @DisplayName("Boş sepet sıfır tutarlı fatura üretir")
    void emptyCart() {
        assertEquals(new BigDecimal("0.00"), invoice(UserTypes.GOLD_CART, NOW).getAmount());
    }
}
