package org.ak.billing.daos;

import org.ak.billing.beans.Product;
import org.ak.billing.daos.impls.FileStoreDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStoreDaoTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Dosya yoksa örnek envanterle oluşturulur")
    void seedsMissingFile() {
        Path file = tempDir.resolve("inventory.csv");

        FileStoreDao dao = new FileStoreDao(file);

        assertTrue(Files.exists(file));
        assertEquals(5, dao.getAllProducts().size());
    }

    @Test
    @DisplayName("Güncellenen stok dosyaya yazılır ve yeni bir örnekten okunur")
    void updatesArePersisted() {
        Path file = tempDir.resolve("inventory.csv");
        FileStoreDao dao = new FileStoreDao(file);
        Product shampoo = dao.getAllProducts().stream()
                .filter(p -> p.getName().equals("ELIDOR SHAMPOO")).findFirst().orElseThrow();

        dao.updateInventoryBatch(Set.of(shampoo.withQuantity(7)));

        assertEquals(7, new FileStoreDao(file).getProduct(shampoo.getId()).getQuantity());
    }

    @Test
    @DisplayName("Bozuk satır, satır numarasıyla birlikte raporlanır")
    void malformedLineIsReported() throws Exception {
        Path file = tempDir.resolve("broken.csv");
        Files.writeString(file, "# yorum\nbozuk,satir\n");

        FileStoreDao dao = new FileStoreDao(file);
        IllegalStateException e = assertThrows(IllegalStateException.class, dao::getAllProducts);

        assertTrue(e.getMessage().contains(":2"), e.getMessage());
    }
}
