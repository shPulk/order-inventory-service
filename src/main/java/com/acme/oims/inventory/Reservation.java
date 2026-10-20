package com.acme.oims.inventory;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "stock_level_id", nullable = false)
    private Long stockLevelId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Reservation() {}

    public Reservation(Long orderId, Long stockLevelId, int quantity) {
        this.orderId = orderId;
        this.stockLevelId = stockLevelId;
        this.quantity = quantity;
    }

    public void markReleased() {
        this.status = ReservationStatus.RELEASED;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getStockLevelId() {
        return stockLevelId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
