package org.ak.billing.strategies.impls;

import org.ak.billing.beans.Product;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.strategies.CartLoadingStrategy;

import java.util.LinkedHashSet;
import java.util.Set;

/** Envanterdeki her üründen {@code loadQuantity} adet alır; biri bile yetmezse hata verir. */
public class MyCartLoadingStrategy implements CartLoadingStrategy {
    @Override
    public Set<Product> loadNEachFromInventory(Set<Product> inventory, int loadQuantity)
            throws InventoryShortageException {
        Set<Product> cartProducts = new LinkedHashSet<>();
        for (Product p : inventory) {
            if (loadQuantity > p.getQuantity()) {
                throw new InventoryShortageException(String.format(
                        "'%s' için stok yetersiz: istenen %d, mevcut %d.", p.getName(), loadQuantity, p.getQuantity()));
            }
            cartProducts.add(p.withQuantity(loadQuantity));
        }
        return cartProducts;
    }
}
