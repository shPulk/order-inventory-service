package com.acme.oims.inventory;

import com.acme.oims.common.BusinessRuleException;
import jakarta.persistence.*;

@Entity
@Table(name = "stock_level")
public class StockLevel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Version
    private long version;

    protected StockLevel() {}

    public StockLevel(Long warehouseId, Long productId, int onHand) {
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.onHand = onHand;
    }

    public int available() {
        return onHand - reserved;
    }

    public void reserve(int qty) {
        if (qty > available()) {
            throw new BusinessRuleException("Insufficient stock on row" + id);
        }
        reserved += qty;
    }

    public void release(int qty) {
        if (qty > reserved){
            throw new IllegalStateException("Releasing" + qty + "but only" + reserved + "reserved om row" + id);
        }
        reserved -= qty;
    }

    public void setOnHand(int newOnHand) {
        if (newOnHand < reserved) {
            throw new BusinessRuleException(
                    "on_hand("+ newOnHand +") cannot be below reserved ("+ reserved +")");
        }
        this.onHand = newOnHand;
    }

    public Long getId() {
        return id;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }
}
