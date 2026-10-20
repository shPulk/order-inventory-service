package com.acme.oims.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    public record Demand(Long productId, int quantity) {
    }

    private record Allocation(StockLevel row, int quantity) {
    }

    private final StockLevelRepository stock;
    private final ReservationRepository reservations;
    private final boolean pessimistic;

    public ReservationService(StockLevelRepository stock, ReservationRepository reservations,
                              @Value("${oims.reservation.strategy:pessimistic}") String strategy) {
        this.stock = stock;
        this.reservations = reservations;
        this.pessimistic = !"Optimistic".equalsIgnoreCase(strategy);
    }

    /**
     * All-or-none. Returns the product ids that could not be fully covered (empty = success).
     * Must run inside the caller's transaction so stock and reservations commit together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> reserveAll(Long orderId, List<Demand> demands) {

        Set<Long> productIds = demands.stream()
                .map(Demand::productId)
                .collect(Collectors.toSet());

        List<StockLevel> rows = pessimistic
                ? stock.lockByProductIds(productIds)
                : stock.findAllByProductIds(productIds);

        Map<Long, List<StockLevel>> byProduct =
                rows.stream()
                        .collect(Collectors.groupingBy(StockLevel::getProductId));

        // 1) plan: no state is changed yet
        List<Allocation> plan = new ArrayList<>();
        List<Long> shortfalls = new ArrayList<>();

        for (Demand d : demands) {
            Optional<StockLevel> best = byProduct
                    .getOrDefault(d.productId(), List.of())
                    .stream()
                    .filter(s -> s.available() >= d.quantity())
                    .min(Comparator
                            .comparingInt(StockLevel::available)
                            .reversed()
                            .thenComparing(StockLevel::getId));

            if (best.isPresent()) {
                plan.add(new Allocation(best.get(), d.quantity()));
            } else {
                shortfalls.add(d.productId());
            }
        }

        if (!shortfalls.isEmpty()) {
            return shortfalls;
        }

        // 2) apply
        for (Allocation a : plan) {
            a.row().reserve(a.quantity());
            reservations.save(
                    new Reservation(
                            orderId,
                            a.row().getId(),
                            a.quantity()
                    )
            );
        }

        return List.of();
    }

}
