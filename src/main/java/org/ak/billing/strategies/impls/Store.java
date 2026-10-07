package org.ak.billing.strategies.impls;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.Products;
import org.ak.billing.constants.ProductTypes;
import org.ak.billing.strategies.StoreDBStrategy;

import java.math.BigDecimal;
import java.util.UUID;

// Singleton, bellek içi örnek envanter (testler ve dosyasız çalıştırma için).
public final class Store implements StoreDBStrategy {

    // Önceden ThreadLocal idi: envanter yalnızca singleton'ı ilk oluşturan thread'de görünüyor,
    // diğer thread'lerde null dönüyordu. Tek bir paylaşılan envanter tutulur.
    private final Products productInventory;

    private Store() {
        productInventory = initialize();
    }

    private static final class StoreLoader {
        private static final Store singleton = new Store();
    }

    public static final Store getStore() {
        return StoreLoader.singleton;
    }

    @Override
    public final Products getProductInventory() {
        return productInventory;
    }

    private final Products initialize() {
        Products inventory = new Products();

        // Add Product 1
        UUID pid = UUID.randomUUID();
        Product p = new Product(pid, "BLUE MEN JACKET", 20, ProductTypes.CLOTHING, new BigDecimal("19.99"));
        inventory.getProducts().put(pid, p);

        // Add Product 2
        pid = UUID.randomUUID();
        p = new Product(pid, "ELIDOR SHAMPOO", 62, ProductTypes.COSMETICS, new BigDecimal("4.99"));
        inventory.getProducts().put(pid, p);

        // Add Product 3
        pid = UUID.randomUUID();
        p = new Product(pid, "MAC AIR PRO", 120, ProductTypes.ELECTRONICS, new BigDecimal("14.99"));
        inventory.getProducts().put(pid, p);

        // Add Product 4
        pid = UUID.randomUUID();
        p = new Product(pid, "SAMSUNG S3 MINI", 20, ProductTypes.PHONE, new BigDecimal("0.99"));
        inventory.getProducts().put(pid, p);

        // Add Product 5
        pid = UUID.randomUUID();
        p = new Product(pid, "CARTDORE Black Royal ", 45, ProductTypes.STATIONERY, new BigDecimal("1.99"));
        inventory.getProducts().put(pid, p);

        return inventory;
    }
}
