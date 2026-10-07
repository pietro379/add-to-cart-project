package org.ak.billing.commands;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.services.impls.MyCartService;

public class AddToCartCommand implements Command {

    private final MyCartService cartService;
    private final Product product;
    private final ShoppingCart cart;
    private Product previous;

    public AddToCartCommand(MyCartService cartService, Product product, ShoppingCart cart) {
        this.cartService = cartService;
        this.product = product;
        this.cart = cart;
    }

    @Override
    public boolean execute() {
        // Ürün zaten sepetteyse eski adedi sakla; geri almada ürünü silmek yerine o adede dönülür.
        previous = cartService.getProduct(product.getId(), cart);
        return cartService.addProduct(product, cart);
    }

    @Override
    public void undo() {
        if (previous == null) {
            cartService.removeProduct(product.getId(), cart);
        } else {
            cartService.updateProduct(previous, cart);
        }
    }

    @Override
    public String describe() {
        return product.getQuantity() + " x " + product.getName() + " ekleme";
    }
}
