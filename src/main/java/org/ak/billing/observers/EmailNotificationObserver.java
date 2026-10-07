package org.ak.billing.observers;

import org.ak.billing.beans.Shopper;
import org.ak.billing.helpers.Utility;

import java.io.PrintStream;
import java.util.List;

/** Fatura e-postası gönderimini simüle eder (iletişim listesinin 2. elemanı e-posta kabul edilir). */
public class EmailNotificationObserver implements InvoiceObserver {
    private final PrintStream out;

    public EmailNotificationObserver() {
        this(System.out);
    }

    public EmailNotificationObserver(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onInvoiceGenerated(Shopper shopper) {
        List<String> contacts = shopper.getUserDetails().getContacts();
        if (contacts == null || contacts.size() < 2) {
            return;
        }
        out.printf("[E-POSTA] %s tutarındaki fatura %s adresine gönderildi.%n",
                Utility.money(shopper.getInvoice().getAmount()), contacts.get(1));
    }
}
