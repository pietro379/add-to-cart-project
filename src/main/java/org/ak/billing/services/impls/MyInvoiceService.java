package org.ak.billing.services.impls;

import org.ak.billing.beans.Shopper;
import org.ak.billing.helpers.Utility;
import org.ak.billing.observers.InvoiceObserver;
import org.ak.billing.services.InvoiceService;
import org.ak.billing.strategies.InvoicingStrategy;

import java.util.ArrayList;
import java.util.List;

public class MyInvoiceService implements InvoiceService {
    private final InvoicingStrategy invoicingStrategy;
    private final List<InvoiceObserver> observers = new ArrayList<>();

    public MyInvoiceService(InvoicingStrategy invoicingStrategy) {
        this.invoicingStrategy = invoicingStrategy;
    }

    @Override
    public void addObserver(InvoiceObserver observer) {
        observers.add(observer);
    }

    @Override
    public void generate(Shopper shopper) {
        invoicingStrategy.generate(shopper);
    }

    @Override
    public void publish(Shopper shopper) {
        observers.forEach(observer -> observer.onInvoiceGenerated(shopper));
    }

    @Override
    public String render(Shopper shopper) {
        if (shopper.getInvoice() == null) {
            throw new IllegalStateException("Önce generate() ile fatura üretilmeli.");
        }
        return Utility.renderInvoice(shopper);
    }
}
