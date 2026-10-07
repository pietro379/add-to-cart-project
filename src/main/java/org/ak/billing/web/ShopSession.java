package org.ak.billing.web;

import org.ak.billing.beans.Invoice;
import org.ak.billing.beans.Product;
import org.ak.billing.beans.Shopper;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.beans.UserDetails;
import org.ak.billing.commands.AddToCartCommand;
import org.ak.billing.commands.Command;
import org.ak.billing.commands.CommandInvoker;
import org.ak.billing.commands.RemoveFromCartCommand;
import org.ak.billing.commands.UpdateQuantityCommand;
import org.ak.billing.constants.UserTypes;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.services.InvoiceService;
import org.ak.billing.services.StoreDBService;
import org.ak.billing.services.impls.MyCartService;
import org.ak.billing.strategies.InvoicingStrategy;
import org.ak.billing.strategies.PaymentStrategy;
import org.ak.billing.strategies.impls.CashOnDeliveryPaymentStrategy;
import org.ak.billing.strategies.impls.CreditCardPaymentStrategy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Bir tarayıcı oturumunun durumu: müşteri tipi, sepet ve geri al/yinele geçmişi. */
public class ShopSession {

    /** Arayüzdeki müşteri seçenekleri; "LOYAL" 3 yıllık müşteriyi temsil eder. */
    public enum CustomerKind {
        GOLD("Gold kart", UserTypes.GOLD_CART, 0),
        SILVER("Silver kart", UserTypes.SILVER_CART, 0),
        AFFILIATE("Mağaza çalışanı", UserTypes.AFFILIATE, 0),
        LOYAL("2 yıldan eski müşteri", UserTypes.CUSTOMER, 3),
        NEW("Yeni müşteri", UserTypes.CUSTOMER, 0);

        final String label;
        final UserTypes userType;
        final int yearsAsCustomer;

        CustomerKind(String label, UserTypes userType, int yearsAsCustomer) {
            this.label = label;
            this.userType = userType;
            this.yearsAsCustomer = yearsAsCustomer;
        }
    }

    private final StoreDBService store;
    private final MyCartService cartService;
    private final InvoiceService invoiceService;
    private final InvoicingStrategy previewStrategy;

    private CustomerKind customer = CustomerKind.NEW;
    private String customerName = "Misafir";
    private ShoppingCart cart;
    private CommandInvoker history;

    public ShopSession(StoreDBService store, MyCartService cartService, InvoiceService invoiceService,
            InvoicingStrategy previewStrategy) {
        this.store = store;
        this.cartService = cartService;
        this.invoiceService = invoiceService;
        this.previewStrategy = previewStrategy;
        reset();
    }

    private void reset() {
        cart = cartService.getNewShoppingCart();
        history = new CommandInvoker();
    }

    public void setCustomer(CustomerKind kind, String name) {
        customer = kind;
        if (name != null && !name.isBlank()) {
            customerName = name.trim();
        }
    }

    public String add(UUID productId, int quantity) {
        Product product = findInventoryProduct(productId);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Adet en az 1 olmalı.");
        }
        return run(new AddToCartCommand(cartService, product.withQuantity(quantity), cart),
                "Stok yetersiz: " + product.getName() + " için en fazla " + available(product) + " adet daha eklenebilir.");
    }

    public String setQuantity(UUID productId, int quantity) {
        return run(new UpdateQuantityCommand(cartService, productId, quantity, cart), "Stok yetersiz.");
    }

    public String remove(UUID productId) {
        Product inCart = Optional.ofNullable(cartService.getProduct(productId, cart))
                .orElseThrow(() -> new IllegalArgumentException("Ürün sepette değil."));
        return run(new RemoveFromCartCommand(cartService, inCart, cart), "Ürün sepette değil.");
    }

    public String undo() {
        return history.undoLastCommand().map(c -> "Geri alındı: " + c.describe()).orElse("Geri alınacak işlem yok.");
    }

    public String redo() {
        return history.redoLastCommand().map(c -> "Yinelendi: " + c.describe()).orElse("Yinelenecek işlem yok.");
    }

    private String run(Command command, String failureMessage) {
        if (!history.executeCommand(command)) {
            throw new IllegalArgumentException(failureMessage);
        }
        return command.describe();
    }

    /** Stokları düşer, faturayı keser, ödemeyi alır ve sepeti boşaltır. */
    public Dto.Receipt checkout(String paymentMethod, String cardNumber) throws InventoryShortageException {
        if (cartService.getAllProducts(cart).isEmpty()) {
            throw new IllegalArgumentException("Sepetiniz boş.");
        }
        // Kart numarası stok düşülmeden önce doğrulanır; geçersizse hiçbir şey değişmez.
        PaymentStrategy payment = "card".equals(paymentMethod)
                ? new CreditCardPaymentStrategy(cardNumber)
                : new CashOnDeliveryPaymentStrategy();

        store.updateInventory(cartService.getAllProducts(cart));
        Shopper shopper = new Shopper(user(), cart);
        invoiceService.generate(shopper);
        String paymentMessage = payment.pay(shopper.getInvoice().getAmount());
        invoiceService.publish(shopper);

        Dto.Receipt receipt = new Dto.Receipt(shopper.getInvoice().getUid().toString(),
                shopper.getInvoice().getDate().toString(), customerName, customer.label,
                cartItems(), totals(shopper.getInvoice()), paymentMessage);
        reset();
        return receipt;
    }

    public Dto.State state(String message) {
        List<Dto.ProductView> products = store.getInventory().stream()
                .map(p -> new Dto.ProductView(p.getId().toString(), p.getName(), p.getType().name(),
                        p.getUnitPrice().toPlainString(), p.getQuantity(), available(p), quantityInCart(p.getId())))
                .collect(Collectors.toList());

        Shopper preview = new Shopper(user(), cart);
        previewStrategy.generate(preview);

        return new Dto.State(customer.name(), customerName, products, cartItems(),
                cartService.getItemCount(cart), totals(preview.getInvoice()),
                history.canUndo(), history.canRedo(), message);
    }

    private List<Dto.CartItem> cartItems() {
        return cartService.getAllProducts(cart).stream()
                .map(p -> new Dto.CartItem(p.getId().toString(), p.getName(), p.getType().name(),
                        p.getUnitPrice().toPlainString(), p.getQuantity(), p.getLineTotal().toPlainString(),
                        p.isPhone()))
                .collect(Collectors.toList());
    }

    private static Dto.Totals totals(Invoice invoice) {
        return new Dto.Totals(invoice.getSubtotal().toPlainString(),
                invoice.getUserDiscountRate().stripTrailingZeros().toPlainString(),
                invoice.getUserDiscount().toPlainString(), invoice.getBillDiscount().toPlainString(),
                invoice.getAmount().toPlainString());
    }

    private UserDetails user() {
        return new UserDetails.Builder()
                .name(customerName)
                .userType(customer.userType)
                .userSince(LocalDateTime.now().minusYears(customer.yearsAsCustomer))
                .contacts("+90 555 000 00 00", "musteri@example.com")
                .build();
    }

    private Product findInventoryProduct(UUID productId) {
        return store.getInventory().stream().filter(p -> p.getId().equals(productId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı."));
    }

    private int quantityInCart(UUID productId) {
        Product inCart = cartService.getProduct(productId, cart);
        return inCart == null ? 0 : inCart.getQuantity();
    }

    private int available(Product product) {
        return product.getQuantity() - quantityInCart(product.getId());
    }

    /** JSON'a dönüştürülen yanıt tipleri. Para değerleri kayıpsız olsun diye metin olarak gönderilir. */
    public static final class Dto {
        private Dto() {
        }

        public record ProductView(String id, String name, String type, String price, int stock, int available,
                int inCart) {
        }

        public record CartItem(String id, String name, String type, String unitPrice, int quantity,
                String lineTotal, boolean phone) {
        }

        public record Totals(String subtotal, String userDiscountRate, String userDiscount, String billDiscount,
                String total) {
        }

        public record State(String customer, String customerName, List<ProductView> products, List<CartItem> cart,
                int itemCount, Totals totals, boolean canUndo, boolean canRedo, String message) {
        }

        public record Receipt(String invoiceId, String date, String customerName, String customerLabel,
                List<CartItem> items, Totals totals, String paymentMessage) {
        }
    }
}
