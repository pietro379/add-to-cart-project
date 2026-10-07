package org.ak.billing.services;

import org.ak.billing.beans.Product;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.observers.InventoryObserver;

import java.util.Set;
import java.util.UUID;

public interface StoreDBService {
    Set<Product> getInventory();

    boolean isTransactionAllowed(UUID pid, int quantity);

    /**
     * Sepetteki adetleri stoktan düşer. Ürünlerden biri bile yetersizse hiçbir stok değişmez.
     */
    void updateInventory(Set<Product> cartProducts) throws InventoryShortageException;

    void addObserver(InventoryObserver observer);

    void removeObserver(InventoryObserver observer);
}
