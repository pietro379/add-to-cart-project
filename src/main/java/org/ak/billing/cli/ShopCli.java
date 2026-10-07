package org.ak.billing.cli;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.Shopper;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.beans.UserDetails;
import org.ak.billing.commands.AddToCartCommand;
import org.ak.billing.commands.Command;
import org.ak.billing.commands.CommandInvoker;
import org.ak.billing.commands.RemoveFromCartCommand;
import org.ak.billing.constants.UserTypes;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.helpers.Utility;
import org.ak.billing.services.InvoiceService;
import org.ak.billing.services.StoreDBService;
import org.ak.billing.services.impls.MyCartService;
import org.ak.billing.strategies.PaymentStrategy;
import org.ak.billing.strategies.impls.CashOnDeliveryPaymentStrategy;
import org.ak.billing.strategies.impls.CreditCardPaymentStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Etkileşimli alışveriş akışı. Girdi ve çıktı dışarıdan verildiği için testte senaryo olarak çalıştırılabilir. */
public class ShopCli {
    private static final String ROW = "%3s  %-24s %-12s %12s %6s";

    private final StoreDBService store;
    private final MyCartService cartService;
    private final InvoiceService invoiceService;
    private final BufferedReader in;
    private final PrintStream out;
    private final CommandInvoker history = new CommandInvoker();

    public ShopCli(StoreDBService store, MyCartService cartService, InvoiceService invoiceService,
            BufferedReader in, PrintStream out) {
        this.store = store;
        this.cartService = cartService;
        this.invoiceService = invoiceService;
        this.in = in;
        this.out = out;
    }

    /** Akışı çalıştırır; ödeme tamamlanırsa {@code true} döner. */
    public boolean run() {
        out.println(Utility.line('='));
        out.println(Utility.center("ADD TO CART · Alışverişe hoş geldiniz"));
        out.println(Utility.line('='));

        Optional<Shopper> shopper = askShopper();
        if (shopper.isEmpty()) {
            return false;
        }
        ShoppingCart cart = shopper.get().getShoppingCart();

        while (true) {
            out.println();
            out.println(cartStatus(cart));
            out.println("[1] Ekle  [2] Çıkar  [3] Sepet  [4] Geri al  [5] Yinele  [6] Ödeme  [0] Çıkış");
            String choice = prompt("Seçiminiz: ");
            if (choice == null || choice.equals("0")) {
                out.println("Alışveriş sonlandırıldı. Görüşmek üzere!");
                return false;
            }
            switch (choice) {
                case "1" -> addProduct(cart);
                case "2" -> removeProduct(cart);
                case "3" -> showCart(cart);
                case "4" -> out.println(history.undoLastCommand()
                        .map(c -> "Geri alındı: " + c.describe())
                        .orElse("Geri alınacak işlem yok."));
                case "5" -> out.println(history.redoLastCommand()
                        .map(c -> "Yinelendi: " + c.describe())
                        .orElse("Yinelenecek işlem yok."));
                case "6" -> {
                    if (checkout(shopper.get())) {
                        return true;
                    }
                }
                default -> out.println("Geçersiz seçim.");
            }
        }
    }

    private Optional<Shopper> askShopper() {
        String name = prompt("Adınız (boş bırakabilirsiniz): ");
        if (name == null) {
            return Optional.empty();
        }
        out.println();
        out.println("Müşteri tipiniz:");
        out.println("  [1] Gold kart                    %30 indirim");
        out.println("  [2] Silver kart                  %20 indirim");
        out.println("  [3] Mağaza çalışanı (affiliate)  %10 indirim");
        out.println("  [4] 2 yıldan eski müşteri        %5 indirim");
        out.println("  [5] Yeni müşteri");
        out.println("  Yüzde indirimler telefonlara uygulanmaz; ayrıca her $200 için $5 indirim vardır.");

        LocalDateTime now = LocalDateTime.now();
        while (true) {
            String choice = prompt("Seçiminiz: ");
            if (choice == null) {
                return Optional.empty();
            }
            UserDetails.Builder user = new UserDetails.Builder()
                    .uid(UUID.randomUUID().toString().substring(0, 8))
                    .name(name.isBlank() ? "Misafir" : name)
                    .contacts("+90 555 000 00 00", "musteri@example.com")
                    .userSince(now);
            switch (choice) {
                case "1" -> user.userType(UserTypes.GOLD_CART);
                case "2" -> user.userType(UserTypes.SILVER_CART);
                case "3" -> user.userType(UserTypes.AFFILIATE);
                case "4" -> user.userType(UserTypes.CUSTOMER).userSince(now.minusYears(3));
                case "5" -> user.userType(UserTypes.CUSTOMER);
                default -> {
                    out.println("Lütfen 1-5 arasında bir sayı girin.");
                    continue;
                }
            }
            return Optional.of(new Shopper(user.build(), cartService.getNewShoppingCart()));
        }
    }

    private void addProduct(ShoppingCart cart) {
        List<Product> inventory = new ArrayList<>(store.getInventory());
        out.println(String.format(ROW, "No", "Ürün", "Tür", "Fiyat", "Stok"));
        out.println(Utility.line('-'));
        for (int i = 0; i < inventory.size(); i++) {
            Product p = inventory.get(i);
            out.println(String.format(ROW, i + 1, Utility.truncate(p.getName(), 24), p.getType(),
                    Utility.money(p.getUnitPrice()), available(p, cart)));
        }

        Integer index = askNumber("Ürün no (vazgeçmek için Enter): ", 1, inventory.size());
        if (index == null) {
            return;
        }
        Product product = inventory.get(index - 1);
        int available = available(product, cart);
        if (available == 0) {
            out.println(product.getName() + " için stok kalmadı.");
            return;
        }
        Integer quantity = askNumber("Adet (1-" + available + ", varsayılan 1): ", 1, available);
        Command add = new AddToCartCommand(cartService, product.withQuantity(quantity == null ? 1 : quantity), cart);
        out.println(history.executeCommand(add) ? "Sepete eklendi: " + add.describe() : "Stok yetersiz, eklenemedi.");
    }

    private void removeProduct(ShoppingCart cart) {
        List<Product> items = new ArrayList<>(cartService.getAllProducts(cart));
        if (items.isEmpty()) {
            out.println("Sepetiniz boş.");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            out.printf("%3d  %s x %d%n", i + 1, items.get(i).getName(), items.get(i).getQuantity());
        }
        Integer index = askNumber("Çıkarılacak ürün no (vazgeçmek için Enter): ", 1, items.size());
        if (index == null) {
            return;
        }
        Command remove = new RemoveFromCartCommand(cartService, items.get(index - 1), cart);
        if (history.executeCommand(remove)) {
            out.println("Sepetten çıkarıldı: " + items.get(index - 1).getName() + " ([4] ile geri alabilirsiniz)");
        }
    }

    private void showCart(ShoppingCart cart) {
        if (cartService.getAllProducts(cart).isEmpty()) {
            out.println("Sepetiniz boş.");
            return;
        }
        out.print(Utility.renderProducts(cartService.getAllProducts(cart)));
        out.println(Utility.line('-'));
        out.println("Ara toplam: " + Utility.money(cartService.getSubtotal(cart))
                + " (indirimler ödeme adımında hesaplanır)");
    }

    private boolean checkout(Shopper shopper) {
        ShoppingCart cart = shopper.getShoppingCart();
        if (cartService.getAllProducts(cart).isEmpty()) {
            out.println("Sepetiniz boş; önce ürün ekleyin.");
            return false;
        }
        try {
            store.updateInventory(cartService.getAllProducts(cart));
        } catch (InventoryShortageException e) {
            out.println("Ödeme yapılamadı: " + e.getMessage());
            return false;
        }

        invoiceService.generate(shopper);
        out.println();
        out.print(invoiceService.render(shopper));

        PaymentStrategy payment = askPayment();
        out.println(payment.pay(shopper.getInvoice().getAmount()));
        invoiceService.publish(shopper);
        out.println("Bizi tercih ettiğiniz için teşekkürler!");
        return true;
    }

    private PaymentStrategy askPayment() {
        while (true) {
            String choice = prompt("Ödeme yöntemi: [1] Kredi kartı  [2] Kapıda ödeme: ");
            if (choice == null || choice.equals("2")) {
                return new CashOnDeliveryPaymentStrategy();
            }
            if (choice.equals("1")) {
                String number = prompt("Kart numarası (test için 4111 1111 1111 1111): ");
                try {
                    return new CreditCardPaymentStrategy(number);
                } catch (IllegalArgumentException e) {
                    out.println(e.getMessage() + " Tekrar deneyin.");
                }
            }
        }
    }

    private String cartStatus(ShoppingCart cart) {
        int count = cartService.getItemCount(cart);
        return count == 0 ? "Sepet: boş"
                : "Sepet: " + count + " ürün · " + Utility.money(cartService.getSubtotal(cart));
    }

    /** Depodaki stok eksi sepetteki adet. */
    private int available(Product product, ShoppingCart cart) {
        Product inCart = cartService.getProduct(product.getId(), cart);
        return product.getQuantity() - (inCart == null ? 0 : inCart.getQuantity());
    }

    /** Boş girdi için {@code null}; aralık dışı veya sayı olmayan girdide tekrar sorar. */
    private Integer askNumber(String message, int min, int max) {
        while (true) {
            String input = prompt(message);
            if (input == null || input.isEmpty()) {
                return null;
            }
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // aşağıda tekrar sorulur
            }
            out.println(min + " ile " + max + " arasında bir sayı girin.");
        }
    }

    private String prompt(String message) {
        out.print(message);
        out.flush();
        try {
            String line = in.readLine();
            return line == null ? null : line.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
