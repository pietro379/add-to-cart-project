package org.ak.billing.services;

import org.ak.billing.beans.Shopper;
import org.ak.billing.observers.InvoiceObserver;

public interface InvoiceService {
    /** Faturayı hesaplar ve {@link Shopper#setInvoice} ile atar. */
    void generate(Shopper shopper);

    /** Ödeme tamamlandıktan sonra gözlemcileri (e-posta, SMS) bilgilendirir. */
    void publish(Shopper shopper);

    /** Faturanın metin hali. */
    String render(Shopper shopper);

    default void print(Shopper shopper) {
        System.out.print(render(shopper));
    }

    void addObserver(InvoiceObserver observer);
}
