package org.ak.billing.commands;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.ShoppingCart;
import org.ak.billing.services.impls.MyCartService;

import java.util.UUID;

/** Sepetteki bir ürünün adedini doğrudan değiştirir (web arayüzündeki +/- düğmeleri). 0 adet ürünü çıkarır. */
public class UpdateQuantityCommand implements Command {

    private final MyCartService cartService;
    private final UUID productId;
    private final int newQuantity;
    private final ShoppingCart cart;
    private Product previous;

    public UpdateQuantityCommand(MyCartService cartService, UUID productId, int newQuantity, ShoppingCart cart) {
        this.cartService = cartService;
        this.productId = productId;
        this.newQuantity = newQuantity;
        this.cart = cart;
    }

    @Override
    public boolean execute() {
        previous = cartService.getProduct(productId, cart);
        if (previous == null || newQuantity < 0 || newQuantity == previous.getQuantity()) {
            return false;
        }
        if (newQuantity == 0) {
            return cartService.removeProduct(productId, cart);
        }
        return cartService.setQuantity(productId, newQuantity, cart);
    }

    @Override
    public void undo() {
        if (previous != null) {
            cartService.restoreProduct(previous, cart);
        }
    }

    @Override
    public String describe() {
        return (previous == null ? "Ürün" : previous.getName()) + " adedini " + newQuantity + " yapma";
    }
}
