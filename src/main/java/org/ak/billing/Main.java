package org.ak.billing;

import org.ak.billing.cli.ShopCli;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Uygulamanın giriş noktası: bağımlılıkları kurar (elle dependency injection) ve CLI'yı başlatır.
 * İlk argüman envanter dosyasının yoludur (varsayılan {@code database.csv}).
 */
public class Main {
    public static void main(String[] args) {
        String inventoryFile = args.length > 0 ? args[0] : "database.csv";

        StoreDBService store = new MyStoreDBService(new FileStoreDao(inventoryFile));
        store.addObserver(new LowStockAlertObserver());

        MyCartService cartService = new MyCartService(store, new MyCartLoadingStrategy());

        InvoiceService invoiceService = new MyInvoiceService(new MyInvoiceGenerator());
        invoiceService.addObserver(new EmailNotificationObserver());
        invoiceService.addObserver(new SmsNotificationObserver());

        // Konsolun kodlamasıyla oku; Türkçe karakterler Windows'ta da doğru gelsin.
        Charset consoleCharset = System.console() != null ? System.console().charset() : Charset.defaultCharset();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, consoleCharset));

        new ShopCli(store, cartService, invoiceService, in, System.out).run();
    }
}
