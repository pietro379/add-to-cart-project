package org.ak.billing.commands;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.services.impls.MyCartService;

import java.util.UUID;

public class RemoveFromCartCommand implements Command {

    private final MyCartService cartService;
    private final UUID productId;
    private final ShoppingCart cart;
    private Product removed;

    public RemoveFromCartCommand(MyCartService cartService, Product product, ShoppingCart cart) {
        this.cartService = cartService;
        this.productId = product.getId();
        this.cart = cart;
    }

    @Override
    public boolean execute() {
        removed = cartService.getProduct(productId, cart);
        return removed != null && cartService.removeProduct(productId, cart);
    }

    @Override
    public void undo() {
        if (removed != null) {
            cartService.restoreProduct(removed, cart);
        }
    }

    @Override
    public String describe() {
        return (removed == null ? "Ürün" : removed.getName()) + " çıkarma";
    }
}
