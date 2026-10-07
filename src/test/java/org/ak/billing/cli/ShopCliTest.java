package org.ak.billing.cli;

import org.ak.billing.daos.impls.FileStoreDao;
import org.ak.billing.services.impls.MyCartService;
import org.ak.billing.services.impls.MyInvoiceService;
import org.ak.billing.services.impls.MyStoreDBService;
import org.ak.billing.strategies.impls.MyCartLoadingStrategy;
import org.ak.billing.strategies.impls.MyInvoiceGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CLI'yı yazılı bir senaryoyla baştan sona çalıştırır. */
class ShopCliTest {

    @TempDir
    Path tempDir;

    private String run(String... inputLines) {
        MyStoreDBService store = new MyStoreDBService(new FileStoreDao(tempDir.resolve("inventory.csv")));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", inputLines) + "\n"));

        new ShopCli(store, new MyCartService(store, new MyCartLoadingStrategy()),
                new MyInvoiceService(new MyInvoiceGenerator()), in, out).run();
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Gold üye 2 ceket alır, kartla öder")
    void goldMemberCheckout() {
        String output = run(
                "Ayşe",          // ad
                "1",             // Gold
                "1", "1", "2",   // ürün ekle: 1. ürün (BLUE MEN JACKET), 2 adet
                "6",             // ödeme
                "1", "4111 1111 1111 1111");

        assertTrue(output.contains("Sepete eklendi: 2 x BLUE MEN JACKET ekleme"), output);
        assertTrue(output.contains("Ayşe (GOLD_CART)"), output);
        assertTrue(output.contains("Üye indirimi (%30, telefon hariç)"), output);
        assertTrue(output.matches("(?s).*ÖDENECEK TUTAR\\s+\\$27\\.99.*"), output); // 39.98 - 11.99
        assertTrue(output.contains("**** **** **** 1111 numaralı karttan $27.99 çekildi."), output);
    }

    @Test
    @DisplayName("Geri al / yinele ve hatalı girdiler akışı bozmaz")
    void undoRedoAndInvalidInput() {
        String output = run(
                "", "9", "5",          // isimsiz, geçersiz tip, yeni müşteri
                "1", "abc", "4", "",   // geçersiz no, 4. ürün, varsayılan adet (1)
                "4",                   // geri al
                "4",                   // geri alınacak işlem yok
                "5",                   // yinele
                "x",                   // geçersiz menü seçimi
                "0");                  // çıkış

        assertTrue(output.contains("Lütfen 1-5 arasında bir sayı girin."), output);
        assertTrue(output.contains("1 ile 5 arasında bir sayı girin."), output);
        assertTrue(output.contains("Geri alındı: 1 x SAMSUNG S3 MINI ekleme"), output);
        assertTrue(output.contains("Geri alınacak işlem yok."), output);
        assertTrue(output.contains("Yinelendi: 1 x SAMSUNG S3 MINI ekleme"), output);
        assertTrue(output.contains("Sepet: 1 ürün · $0.99"), output);
        assertTrue(output.contains("Geçersiz seçim."), output);
        assertFalse(output.contains("ÖDENECEK TUTAR"), output);
    }

    @Test
    @DisplayName("Boş sepetle ödeme yapılamaz")
    void emptyCartCheckout() {
        String output = run("Ali", "5", "6", "0");

        assertTrue(output.contains("Sepetiniz boş; önce ürün ekleyin."), output);
    }
}
