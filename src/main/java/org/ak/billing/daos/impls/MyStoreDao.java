package org.ak.billing.daos.impls;

import org.ak.billing.beans.Product;
import org.ak.billing.daos.StoreDao;
import org.ak.billing.strategies.StoreDBStrategy;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class MyStoreDao implements StoreDao {

    StoreDBStrategy myStore;

    public MyStoreDao(StoreDBStrategy myStore) {
        this.myStore = myStore;
    }

    @Override
    public boolean updateInventory(Product product) {
        boolean response = false;
        if (myStore.getProductInventory().getProducts().containsKey(product.getId())) {
            myStore.getProductInventory().getProducts().put(product.getId(), product);
            response = true;
        }
        return response;
    }

    @Override
    public boolean updateInventoryBatch(Set<Product> products) {
        boolean allUpdated = true;
        for (Product p : products) {
            allUpdated &= updateInventory(p);
        }
        return allUpdated;
    }

    @Override
    public Product getProduct(UUID pid) {
        Product product = null;
        if (myStore.getProductInventory().getProducts().containsKey(pid)) {
            product = myStore.getProductInventory().getProducts().get(pid);
        }
        return product;
    }

    @Override
    public Set<Product> getAllProducts() {
        return new LinkedHashSet<>(myStore.getProductInventory().getProducts().values());
    }
}
