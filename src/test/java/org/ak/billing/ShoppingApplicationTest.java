package org.ak.billing;

import org.ak.billing.beans.Product;
import org.ak.billing.beans.UserDetails;
import org.ak.billing.constants.UserTypes;
import org.ak.billing.daos.impls.FileStoreDao;
import org.ak.billing.exceptions.InventoryShortageException;
import org.ak.billing.services.StoreDBService;
import org.ak.billing.services.impls.MyCartService;
import org.ak.billing.services.impls.MyInvoiceService;
import org.ak.billing.services.impls.MyStoreDBService;
import org.ak.billing.strategies.impls.MyCartLoadingStrategy;
import org.ak.billing.strategies.impls.MyInvoiceGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Uçtan uca senaryo: varsayılan envanterdeki 5 üründen 2'şer adet alınır.
 * Ara toplam $85.90; bunun $1.98'i telefon (yüzde indirim dışı), $83.92'si indirime tabi.
 */
class ShoppingApplicationTest {

    @TempDir
    Path tempDir;

    private StoreDBService store;
    private ShoppingApplication app;

    @BeforeEach
    void setUp() {
        store = new MyStoreDBService(new FileStoreDao(tempDir.resolve("inventory.csv")));
        app = new ShoppingApplication(store,
                new MyCartService(store, new MyCartLoadingStrategy()),
                new MyInvoiceService(new MyInvoiceGenerator()) {
                    @Override
                    public void print(org.ak.billing.beans.Shopper shopper) {
                        // testte konsola fatura basma
                    }
                });
    }

    @ParameterizedTest(name = "{0} -> ${2}")
    @CsvSource({
            "GOLD_CART,   0, 60.72",  // 83.92 x %30 = 25.18 indirim
            "SILVER_CART, 0, 69.12",  // 83.92 x %20 = 16.78
            "AFFILIATE,   0, 77.51",  // 83.92 x %10 = 8.39
            "CUSTOMER,    3, 81.70",  // 3 yıllık müşteri, 83.92 x %5 = 4.20
            "CUSTOMER,    0, 85.90",  // yeni müşteri, indirim yok
    })
    @DisplayName("Kullanıcı tipine göre net tutar")
    void netAmountByUserType(UserTypes type, int yearsAsCustomer, String expected) throws Exception {
        UserDetails user = new UserDetails.Builder()
                .name("Test")
                .userType(type)
                .userSince(LocalDateTime.now().minusYears(yearsAsCustomer))
                .build();

        assertEquals(new BigDecimal(expected), app.shop(user));
    }

    @Test
    @DisplayName("Alışveriş sonrası stoklar düşer")
    void shoppingDecreasesInventory() throws Exception {
        int before = store.getInventory().stream().mapToInt(Product::getQuantity).sum();

        app.shop(new UserDetails.Builder().userType(UserTypes.CUSTOMER).userSince(LocalDateTime.now()).build());

        int after = store.getInventory().stream().mapToInt(Product::getQuantity).sum();
        assertEquals(before - 5 * 2, after);
    }

    @Test
    @DisplayName("Stok yetersizse alışveriş başlamaz")
    void shortageIsReported() {
        UserDetails user = new UserDetails.Builder().userType(UserTypes.CUSTOMER).userSince(LocalDateTime.now()).build();
        // En az stoklu ürün 20 adet: 10 tur x 2 adet stoku bitirir, 11. tur yetmez.
        assertThrows(InventoryShortageException.class, () -> {
            for (int i = 0; i < 11; i++) {
                app.shop(user);
            }
        });
    }
}
