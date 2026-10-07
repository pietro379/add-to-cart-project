package org.ak.billing.commands;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.daos.impls.FileStoreDao;
import org.ak.billing.services.impls.MyCartService;
import org.ak.billing.services.impls.MyStoreDBService;
import org.ak.billing.strategies.impls.MyCartLoadingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartCommandsTest {

    @TempDir
    Path tempDir;

    private MyCartService cartService;
    private ShoppingCart cart;
    private CommandInvoker invoker;
    private Product jacket; // stok: 20

    @BeforeEach
    void setUp() {
        MyStoreDBService store = new MyStoreDBService(new FileStoreDao(tempDir.resolve("inventory.csv")));
        cartService = new MyCartService(store, new MyCartLoadingStrategy());
        cart = cartService.getNewShoppingCart();
        invoker = new CommandInvoker();
        jacket = store.getInventory().stream().filter(p -> p.getName().equals("BLUE MEN JACKET")).findFirst().orElseThrow();
    }

    private boolean add(int quantity) {
        return invoker.executeCommand(new AddToCartCommand(cartService, jacket.withQuantity(quantity), cart));
    }

    private Integer quantityInCart() {
        Product p = cartService.getProduct(jacket.getId(), cart);
        return p == null ? null : p.getQuantity();
    }

    @Test
    @DisplayName("Aynı ürün tekrar eklenince adetler toplanır")
    void addingSameProductMergesQuantity() {
        add(2);
        add(3);

        assertEquals(5, quantityInCart());
        assertEquals(1, cartService.getAllProducts(cart).size());
    }

    @Test
    @DisplayName("Geri alma ürünü silmez, önceki adede döner")
    void undoRestoresPreviousQuantity() {
        add(2);
        add(3);

        invoker.undoLastCommand();
        assertEquals(2, quantityInCart());

        invoker.undoLastCommand();
        assertNull(quantityInCart());
    }

    @Test
    @DisplayName("Yinele geri alınan işlemi tekrar uygular")
    void redoReappliesCommand() {
        add(2);
        invoker.undoLastCommand();

        assertTrue(invoker.redoLastCommand().isPresent());
        assertEquals(2, quantityInCart());
        assertFalse(invoker.canRedo());
    }

    @Test
    @DisplayName("Yeni işlem yinele geçmişini temizler")
    void newCommandClearsRedo() {
        add(1);
        invoker.undoLastCommand();
        add(4);

        assertFalse(invoker.canRedo());
    }

    @Test
    @DisplayName("Stoku aşan ekleme başarısız olur ve geçmişe girmez")
    void addingBeyondStockFails() {
        add(15);

        assertFalse(add(6)); // 15 + 6 > 20
        assertEquals(15, quantityInCart());

        invoker.undoLastCommand(); // başarısız ekleme değil, 15'lik ekleme geri alınır
        assertNull(quantityInCart());
        assertFalse(invoker.canUndo());
    }

    @Test
    @DisplayName("Çıkarma geri alınınca ürün aynı adetle sepete döner")
    void undoRemove() {
        add(4);
        invoker.executeCommand(new RemoveFromCartCommand(cartService, jacket, cart));
        assertNull(quantityInCart());

        invoker.undoLastCommand();
        assertEquals(4, quantityInCart());
    }

    @Test
    @DisplayName("Sepette olmayan ürünü çıkarmak başarısız olur")
    void removeMissingProduct() {
        assertFalse(invoker.executeCommand(new RemoveFromCartCommand(cartService, jacket, cart)));
        assertFalse(invoker.canUndo());
    }

    @Test
    @DisplayName("getAllProducts bir kopya döner; üzerinde değişiklik sepeti etkilemez")
    void getAllProductsIsACopy() {
        add(1);
        cartService.getAllProducts(cart).clear();

        assertEquals(1, quantityInCart());
    }
}
