package org.ak.billing.strategies.impls;

import org.ak.billing.beans.Shopper;
import org.ak.billing.strategies.InvoicingStrategy;

/**
 * Eski, {@code double} ile hesap yapan ve telefonlara da kullanıcı indirimi uygulayan sürümün yerini tutar.
 * Para hesabında kayan nokta hatası ve kural 6 ihlali nedeniyle artık {@link MyInvoiceGenerator}'a devreder.
 *
 * @deprecated {@link MyInvoiceGenerator} kullanın.
 */
@Deprecated
public class MySecondInvoiceGenerator implements InvoicingStrategy {
    private final MyInvoiceGenerator delegate = new MyInvoiceGenerator();

    @Override
    public void generate(Shopper shopper) {
        delegate.generate(shopper);
    }
}
