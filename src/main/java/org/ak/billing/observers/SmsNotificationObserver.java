package org.ak.billing.observers;

import org.ak.billing.beans.Shopper;

import java.io.PrintStream;
import java.util.List;

/** Fatura SMS'i gönderimini simüle eder (iletişim listesinin 1. elemanı telefon kabul edilir). */
public class SmsNotificationObserver implements InvoiceObserver {
    private final PrintStream out;

    public SmsNotificationObserver() {
        this(System.out);
    }

    public SmsNotificationObserver(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onInvoiceGenerated(Shopper shopper) {
        List<String> contacts = shopper.getUserDetails().getContacts();
        if (contacts == null || contacts.isEmpty()) {
            return;
        }
        out.printf("[SMS] Fatura bilgisi %s numarasına gönderildi.%n", contacts.get(0));
    }
}
