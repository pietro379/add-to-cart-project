package org.ak.billing.services;

import org.ak.billing.beans.Product;
import org.ak.billing.daos.impls.FileStoreDao;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.observers.InventoryObserver;
import org.ak.billing.services.impls.MyStoreDBService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyStoreDBServiceTest {

    @TempDir
    Path tempDir;

    private MyStoreDBService store;
    private Map<String, Product> byName;

    @BeforeEach
    void setUp() {
        store = new MyStoreDBService(new FileStoreDao(tempDir.resolve("inventory.csv")));
        byName = store.getInventory().stream().collect(Collectors.toMap(Product::getName, Function.identity()));
    }

    private int stockOf(String name) {
        return store.getInventory().stream().filter(p -> p.getName().equals(name))
                .findFirst().orElseThrow().getQuantity();
    }

    @Test
    @DisplayName("Ödeme stokları düşer ve gözlemcilere haber verir")
    void updateInventoryDecreasesStock() throws Exception {
        List<Product> notified = new ArrayList<>();
        InventoryObserver observer = notified::add;
        store.addObserver(observer);

        store.updateInventory(Set.of(byName.get("MAC AIR PRO").withQuantity(20)));

        assertEquals(100, stockOf("MAC AIR PRO"));
        assertEquals(List.of("MAC AIR PRO"), notified.stream().map(Product::getName).toList());
    }

    @Test
    @DisplayName("Bir ürünün stoku yetmezse hiçbir stok değişmez")
    void shortageIsAtomic() {
        List<Product> notified = new ArrayList<>();
        InventoryObserver observer = notified::add;
        store.addObserver(observer);
        Set<Product> cart = new LinkedHashSet<>();
        cart.add(byName.get("MAC AIR PRO").withQuantity(10));
        cart.add(byName.get("SAMSUNG S3 MINI").withQuantity(21)); // stok 20

        InventoryShortageException e = assertThrows(InventoryShortageException.class, () -> store.updateInventory(cart));

        assertEquals("'SAMSUNG S3 MINI' için stok yetersiz: istenen 21, mevcut 20.", e.getMessage());
        assertEquals(120, stockOf("MAC AIR PRO"));
        assertTrue(notified.isEmpty());
    }
}
