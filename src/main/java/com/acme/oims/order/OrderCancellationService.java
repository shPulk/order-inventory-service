package com.acme.oims.order;

import com.acme.oims.common.NotFoundException;
import com.acme.oims.inventory.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderCancellationService {

    private final OrderRepository orders;
    private final ReservationRepository reservations;
    private final StockLevelRepository stock;

    public OrderCancellationService(OrderRepository orders, ReservationRepository reservations, StockLevelRepository stock) {
        this.orders = orders;
        this.reservations = reservations;
        this.stock = stock;
    }

    /** Idempotent: cancelling an already canceled order returns it unchanged. */
    @Transactional
    public OrderResponse cancel(Long id) {
        Order order = orders.findByIdForUpdate(id)
                .orElseThrow(()-> new NotFoundException("Order" + id + "not found"));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }
        cancelLocked(order);  // illegal status (SHIPPED, DELIVERED, FAILED)
        return OrderResponse.from(order);
    }

    /** Returns true only if THIS call epired the order. Re-checks under the row lock. */
    @Transactional
    public boolean expire(Long id, Instant now) {
        Order order = orders.findByIdForUpdate(id).orElse(null);
        if (order == null
                || order.getStatus() != OrderStatus.RESERVED
                || order.getExpiresAt() == null
                || !order.getExpiresAt().isBefore(now)) {
            return false;     // paid, canceled, or handled by another instance
        }
        cancelLocked(order);
        return true;
    }

    /** Caller must hold the order row lock. */
    private void cancelLocked(Order order) {
        order.transitionTo(OrderStatus.CANCELLED);
        List<Reservation> active = reservations.findByOrderIdAndStatus(order.getId(), ReservationStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        Set<Long> stockIds = active.stream().map(Reservation::getStockLevelId).collect(Collectors.toSet());
        Map<Long, StockLevel> rows = stock.lockByIds(stockIds).stream()
                .collect(Collectors.toMap(StockLevel::getId, Function.identity()));
        for (Reservation r : active) {
            rows.get(r.getStockLevelId()).release(r.getQuantity());
            r.markReleased();
        }
    }
}
