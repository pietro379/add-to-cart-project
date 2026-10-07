package org.ak.billing.services.impls;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.services.ShoppingCartService;
import org.ak.billing.services.StoreDBService;
import org.ak.billing.strategies.CartLoadingStrategy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class MyCartService implements ShoppingCartService {

    private final StoreDBService myStoreDBService;
    private final CartLoadingStrategy myCartLoadingStrategy;

    public MyCartService(StoreDBService myStoreDBService,
                         CartLoadingStrategy myCartLoadingStrategy) {
        this.myStoreDBService = myStoreDBService;
        this.myCartLoadingStrategy = myCartLoadingStrategy;
    }

    /**
     * Ürünü sepete ekler. Ürün zaten sepetteyse adetler toplanır.
     * Sepetteki toplam adet stoku aşacaksa hiçbir şey değişmez ve {@code false} döner.
     */
    @Override
    public boolean addProduct(Product product, ShoppingCart shoppingCart) {
        if (product.getQuantity() <= 0) {
            return false;
        }
        LinkedHashMap<UUID, Product> productsInCart = shoppingCart.getProductsInCart().getProducts();
        Product existing = productsInCart.get(product.getId());
        int newQuantity = product.getQuantity() + (existing == null ? 0 : existing.getQuantity());

        if (!myStoreDBService.isTransactionAllowed(product.getId(), newQuantity)) {
            return false;
        }
        productsInCart.put(product.getId(), product.withQuantity(newQuantity));
        return true;
    }

    @Override
    public boolean addProductBatch(Set<Product> products, ShoppingCart shoppingCart) {
        boolean response = false;
        for (Product p : products) {
            response = addProduct(p, shoppingCart);
            if (!response) {
                break;
            }
        }
        return response;
    }

    @Override
    public boolean removeProduct(UUID pid, ShoppingCart shoppingCart) {
        return shoppingCart.getProductsInCart().getProducts().remove(pid) != null;
    }

    /** Sepetteki bir ürünün kaydını olduğu gibi değiştirir (ör. geri almada önceki adede dönmek için). */
    @Override
    public boolean updateProduct(Product product, ShoppingCart shoppingCart) {
        LinkedHashMap<UUID, Product> productsInCart = shoppingCart.getProductsInCart().getProducts();
        if (!productsInCart.containsKey(product.getId())) {
            return false;
        }
        productsInCart.put(product.getId(), product);
        return true;
    }

    /** Sepetteki ürünün adedini stok kontrolüyle değiştirir. */
    public boolean setQuantity(UUID pid, int quantity, ShoppingCart shoppingCart) {
        Product existing = getProduct(pid, shoppingCart);
        if (existing == null || quantity <= 0 || !myStoreDBService.isTransactionAllowed(pid, quantity)) {
            return false;
        }
        shoppingCart.getProductsInCart().getProducts().put(pid, existing.withQuantity(quantity));
        return true;
    }

    /** Geri alma için: ürünü stok kontrolü yapmadan, verilen adetle sepete geri koyar. */
    public void restoreProduct(Product product, ShoppingCart shoppingCart) {
        shoppingCart.getProductsInCart().getProducts().put(product.getId(), product);
    }

    @Override
    public Product getProduct(UUID pid, ShoppingCart shoppingCart) {
        return shoppingCart.getProductsInCart().getProducts().get(pid);
    }

    /** Sepetin bir kopyası; dönen küme üzerinde yapılan değişiklik sepeti etkilemez. */
    @Override
    public Set<Product> getAllProducts(ShoppingCart shoppingCart) {
        return new LinkedHashSet<>(shoppingCart.getProductsInCart().getProducts().values());
    }

    public BigDecimal getSubtotal(ShoppingCart shoppingCart) {
        return shoppingCart.getProductsInCart().getProducts().values().stream()
                .map(Product::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getItemCount(ShoppingCart shoppingCart) {
        return shoppingCart.getProductsInCart().getProducts().values().stream()
                .mapToInt(Product::getQuantity)
                .sum();
    }

    @Override
    public boolean loadNEachFromInventory(int loadQuantity, ShoppingCart shoppingCart) throws InventoryShortageException {
        return addProductBatch(myCartLoadingStrategy
                .loadNEachFromInventory(myStoreDBService.getInventory(), loadQuantity), shoppingCart);
    }

    @Override
    public ShoppingCart getNewShoppingCart() {
        return new ShoppingCart();
    }
}
