package org.ak.billing.daos.impls;

import org.ak.billing.beans.Product;
import org.ak.billing.constants.ProductTypes;
import org.ak.billing.daos.StoreDao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Envanteri CSV dosyasında tutar: {@code id,ad,adet,tip,birimFiyat}. Dosya yoksa örnek ürünlerle oluşturulur.
 * '#' ile başlayan satırlar yorum kabul edilir.
 */
public class FileStoreDao implements StoreDao {
    private static final String HEADER = "# id,name,quantity,type,unitPrice";

    private final Path file;

    public FileStoreDao(String filePath) {
        this(Path.of(filePath));
    }

    public FileStoreDao(Path file) {
        this.file = file;
        if (Files.notExists(file)) {
            writeToFile(defaultInventory());
        }
    }

    static List<Product> defaultInventory() {
        List<Product> products = new ArrayList<>();
        products.add(new Product(UUID.randomUUID(), "BLUE MEN JACKET", 20, ProductTypes.CLOTHING, new BigDecimal("19.99")));
        products.add(new Product(UUID.randomUUID(), "ELIDOR SHAMPOO", 62, ProductTypes.COSMETICS, new BigDecimal("4.99")));
        products.add(new Product(UUID.randomUUID(), "MAC AIR PRO", 120, ProductTypes.ELECTRONICS, new BigDecimal("14.99")));
        products.add(new Product(UUID.randomUUID(), "SAMSUNG S3 MINI", 20, ProductTypes.PHONE, new BigDecimal("0.99")));
        products.add(new Product(UUID.randomUUID(), "CARTDORE Black Royal", 45, ProductTypes.STATIONERY, new BigDecimal("1.99")));
        return products;
    }

    private Map<UUID, Product> readFromFile() {
        Map<UUID, Product> products = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Envanter dosyası okunamadı: " + file, e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Product product = parse(line, i + 1);
            products.put(product.getId(), product);
        }
        return products;
    }

    private Product parse(String line, int lineNumber) {
        String[] parts = line.split(",");
        if (parts.length != 5) {
            throw new IllegalStateException(String.format("%s:%d hatalı satır (5 alan bekleniyordu): %s",
                    file, lineNumber, line));
        }
        try {
            return new Product(UUID.fromString(parts[0].trim()), parts[1].trim(), Integer.parseInt(parts[2].trim()),
                    ProductTypes.valueOf(parts[3].trim()), new BigDecimal(parts[4].trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(String.format("%s:%d okunamadı: %s", file, lineNumber, e.getMessage()), e);
        }
    }

    // Önce geçici dosyaya yazıp sonra taşır; yazma yarıda kesilirse envanter bozulmaz.
    private void writeToFile(Iterable<Product> products) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Product p : products) {
            if (p.getName().contains(",")) {
                throw new IllegalArgumentException("Ürün adı virgül içeremez: " + p.getName());
            }
            lines.add(String.join(",", p.getId().toString(), p.getName(), String.valueOf(p.getQuantity()),
                    p.getType().name(), p.getUnitPrice().toPlainString()));
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            Path temp = Files.createTempFile(parent, "inventory", ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Envanter dosyası yazılamadı: " + file, e);
        }
    }

    @Override
    public boolean updateInventory(Product product) {
        Map<UUID, Product> products = readFromFile();
        products.put(product.getId(), product);
        writeToFile(products.values());
        return true;
    }

    @Override
    public boolean updateInventoryBatch(Set<Product> updated) {
        Map<UUID, Product> products = readFromFile();
        boolean allKnown = true;
        for (Product p : updated) {
            allKnown &= products.containsKey(p.getId());
            products.put(p.getId(), p);
        }
        writeToFile(products.values());
        return allKnown;
    }

    @Override
    public Product getProduct(UUID pid) {
        return readFromFile().get(pid);
    }

    @Override
    public Set<Product> getAllProducts() {
        return new LinkedHashSet<>(readFromFile().values());
    }
}
