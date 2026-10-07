package org.ak.billing.services.impls;

import org.ak.billing.beans.Product;
import org.ak.billing.daos.StoreDao;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.observers.InventoryObserver;
import org.ak.billing.services.StoreDBService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MyStoreDBService implements StoreDBService {

    private final StoreDao storeDao;
    private final List<InventoryObserver> observers = new ArrayList<>();

    public MyStoreDBService(StoreDao storeDao) {
        this.storeDao = storeDao;
    }

    @Override
    public Set<Product> getInventory() {
        return storeDao.getAllProducts();
    }

    @Override
    public boolean isTransactionAllowed(UUID pid, int quantity) {
        Product product = storeDao.getProduct(pid);
        return product != null && quantity > 0 && product.getQuantity() >= quantity;
    }

    @Override
    public void updateInventory(Set<Product> cartProducts) throws InventoryShortageException {
        // Önce hepsini doğrula: yarım kalmış bir ödeme stokları tutarsız bırakmasın.
        Set<Product> inventoryToUpdate = new LinkedHashSet<>(cartProducts.size());
        for (Product p : cartProducts) {
            Product storeProduct = storeDao.getProduct(p.getId());
            if (storeProduct == null || storeProduct.getQuantity() < p.getQuantity()) {
                int available = storeProduct == null ? 0 : storeProduct.getQuantity();
                throw new InventoryShortageException(String.format(
                        "'%s' için stok yetersiz: istenen %d, mevcut %d.", p.getName(), p.getQuantity(), available));
            }
            inventoryToUpdate.add(storeProduct.withQuantity(storeProduct.getQuantity() - p.getQuantity()));
        }

        if (!inventoryToUpdate.isEmpty()) {
            storeDao.updateInventoryBatch(inventoryToUpdate);
            inventoryToUpdate.forEach(this::notifyObservers);
        }
    }

    @Override
    public void addObserver(InventoryObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(InventoryObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(Product product) {
        for (InventoryObserver observer : observers) {
            observer.onProductStockChanged(product);
        }
    }
}
