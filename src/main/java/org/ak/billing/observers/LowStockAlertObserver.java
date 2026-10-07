package org.ak.billing.observers;

import org.ak.billing.beans.Product;
import org.ak.billing.constants.ApplicationConstants;

import java.io.PrintStream;

public class LowStockAlertObserver implements InventoryObserver {
    private final PrintStream out;

    public LowStockAlertObserver() {
        this(System.out);
    }

    public LowStockAlertObserver(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onProductStockChanged(Product product) {
        if (product.getQuantity() < ApplicationConstants.LOW_STOCK_THRESHOLD.asInt()) {
            out.printf("[STOK UYARISI] '%s' kritik seviyede: %d adet kaldı.%n",
                    product.getName(), product.getQuantity());
        }
    }
}
