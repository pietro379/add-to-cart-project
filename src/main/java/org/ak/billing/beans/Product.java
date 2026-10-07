package org.ak.billing.beans;

import org.ak.billing.constants.ProductTypes;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class Product implements Cloneable {
    private final UUID id;
    private final String name;
    private int quantity;
    private final ProductTypes type;
    private final BigDecimal unitPrice;

    public Product(UUID id, String name, int quantity, ProductTypes type, BigDecimal unitPrice) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name").trim();
        this.type = Objects.requireNonNull(type, "type");
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Birim fiyat negatif olamaz: " + unitPrice);
        }
        setQuantity(quantity);
    }

    /** Aynı ürünün farklı adetteki kopyası; sepete eklerken stok kaydını değiştirmemek için kullanılır. */
    public Product withQuantity(int newQuantity) {
        return new Product(id, name, newQuantity, type, unitPrice);
    }

    /** Birim fiyat x adet. */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public boolean isPhone() {
        return type == ProductTypes.PHONE;
    }

    // Ürün kimliği yalnızca id'dir; adet ve fiyat değişse de aynı ürün sayılır.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Product)) {
            return false;
        }
        return id.equals(((Product) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public UUID getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Adet negatif olamaz: " + quantity);
        }
        this.quantity = quantity;
    }

    public ProductTypes getType() {
        return type;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", type=" + type +
                ", unitPrice=" + unitPrice +
                '}';
    }
}
