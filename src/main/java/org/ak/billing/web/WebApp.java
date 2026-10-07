package org.ak.billing.web;

import org.ak.billing.daos.impls.FileStoreDao;
import org.ak.billing.observers.EmailNotificationObserver;
import org.ak.billing.observers.LowStockAlertObserver;
import org.ak.billing.observers.SmsNotificationObserver;
import org.ak.billing.services.InvoiceService;
import org.ak.billing.services.StoreDBService;
import org.ak.billing.services.impls.MyCartService;
import org.ak.billing.services.impls.MyInvoiceService;
import org.ak.billing.services.impls.MyStoreDBService;
import org.ak.billing.strategies.impls.MyCartLoadingStrategy;
import org.ak.billing.strategies.impls.MyInvoiceGenerator;

import java.io.IOException;

/**
 * Web arayüzünün giriş noktası. Argümanlar: [envanter dosyası] [port].
 * Varsayılanlar: {@code database.csv} ve 8080.
 */
public class WebApp {
    public static void main(String[] args) throws IOException {
        String inventoryFile = args.length > 0 ? args[0] : "database.csv";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        StoreDBService store = new MyStoreDBService(new FileStoreDao(inventoryFile));
        store.addObserver(new LowStockAlertObserver());
        MyCartService cartService = new MyCartService(store, new MyCartLoadingStrategy());
        MyInvoiceGenerator invoiceGenerator = new MyInvoiceGenerator();
        InvoiceService invoiceService = new MyInvoiceService(invoiceGenerator);
        invoiceService.addObserver(new EmailNotificationObserver());
        invoiceService.addObserver(new SmsNotificationObserver());

        WebServer server = new WebServer(() -> new ShopSession(store, cartService, invoiceService, invoiceGenerator));
        int actualPort = server.start(port);
        System.out.println("Add To Cart web arayüzü: http://localhost:" + actualPort + "  (durdurmak için Ctrl+C)");
    }
}
