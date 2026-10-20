package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.common.BusinessRuleException;
import com.acme.oims.common.NotFoundException;
import com.acme.oims.inventory.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public record PlacementResult(
            OrderResponse order,
            boolean created,
            List<Long> shortProducts
    ) {
    }

    private final OrderRepository orders;
    private final ProductRepository products;
    private final ReservationService reservations;
    private final TransactionTemplate tx;
    private final Duration ttl;
    private final int maxAttempts;

    public OrderService(
            OrderRepository orders,
            ProductRepository products,
            ReservationService reservations,
            PlatformTransactionManager txManager,
            @Value("${oims.reservation.ttl-minutes:15}") long ttlMinutes,
            @Value("${oims.reservation.strategy:pessimistic}") String strategy,
            @Value("${oims.reservation.max-attempts:5}") int optimisticAttempts
    ) {
        this.orders = orders;
        this.products = products;
        this.reservations = reservations;
        this.tx = new TransactionTemplate(txManager);
        this.ttl = Duration.ofMinutes(ttlMinutes);

        // pessimistic locking waits instead of failing, so it never needs a retry loop
        this.maxAttempts = "optimistic".equalsIgnoreCase(strategy)
                ? optimisticAttempts
                : 1;
    }

    public PlacementResult place(CreateOrderRequest req, String idempotencyKey) {
        PlacementResult result = placeWithRetry(req, idempotencyKey);

        if (result.created() && !result.shortProducts().isEmpty()) {
            // thrown AFTER commit, so the FAILED order is kept
            throw new InsufficientStockException(
                    result.order().id(),
                    result.shortProducts()
            );
        }

        return result;
    }

    private PlacementResult placeWithRetry(
            CreateOrderRequest req,
            String idempotencyKey
    ) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> placeOnce(req, idempotencyKey));
            } catch (ConcurrencyFailureException e) {
                if (attempt >= maxAttempts) {
                    log.warn(
                            "Stock update conflict, giving up after {} attempts",
                            attempt
                    );
                    throw e;
                }

                log.debug(
                        "Stock update conflict, retry {}/{}",
                        attempt,
                        maxAttempts
                );

                backoff();
            }
        }
    }

    private PlacementResult placeOnce(
            CreateOrderRequest req,
            String idempotencyKey
    ) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? null
                : idempotencyKey.trim();

        if (key != null && key.length() > 64) {
            throw new BusinessRuleException(
                    "Idempotency-Key must be at most 64 characters"
            );
        }

        if (key != null) {
            var existing = orders.findByIdempotencyKey(key);

            if (existing.isPresent()) {
                if (!existing.get().getCustomerId().equals(req.customerId())) {
                    throw new BusinessRuleException(
                            "Idempotency-Key was already used by another customer"
                    );
                }

                return new PlacementResult(
                        OrderResponse.from(existing.get()),
                        false,
                        List.of()
                );
            }
        }

        Set<Long> ids = new HashSet<>();

        for (var line : req.lines()) {
            if (!ids.add(line.productId())) {
                throw new BusinessRuleException(
                        "Duplicate product in order lines: " + line.productId()
                );
            }
        }

        Map<Long, Product> byId = products.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        Function.identity()
                ));

        if (byId.size() != ids.size()) {
            Set<Long> missing = new HashSet<>(ids);
            missing.removeAll(byId.keySet());

            throw new BusinessRuleException(
                    "Unknown product ids: " + missing
            );
        }

        Order order = new Order(req.customerId(), key);

        for (var line : req.lines()) {
            order.addLine(
                    line.productId(),
                    line.quantity(),
                    byId.get(line.productId()).getPrice()
            );
        }

        order = orders.save(order);

        List<ReservationService.Demand> demands = req.lines().stream()
                .map(l -> new ReservationService.Demand(
                        l.productId(),
                        l.quantity()
                ))
                .toList();

        List<Long> shortfalls = reservations.reserveAll(
                order.getId(),
                demands
        );

        if (shortfalls.isEmpty()) {
            order.transitionTo(OrderStatus.RESERVED);
            order.setExpiresAt(Instant.now().plus(ttl));
        } else {
            order.transitionTo(OrderStatus.FAILED);
        }

        return new PlacementResult(
                OrderResponse.from(order),
                true,
                shortfalls
        );
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(find(id));
    }

    @Transactional
    public OrderResponse transition(Long id, OrderStatus target) {
        if (target == OrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Use the cancel endpoint to cancel an order"
            );
        }

        if (target == OrderStatus.RESERVED) {
            throw new BusinessRuleException(
                    "RESERVED is set only by the reservation engine"
            );
        }

        Order order = orders.findByIdForUpdate(id)
                .orElseThrow(()-> new NotFoundException("Order" + id + "not found"));
        if (target == OrderStatus.PAID && order.getStatus() == OrderStatus.RESERVED
                && order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Reservation expired, place a new order");
        }
        order.transitionTo(target);
        return OrderResponse.from(order);
    }

    private Order find(Long id) {
        return orders.findById(id)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Order " + id + " not found"
                        ));
    }

    private static void backoff() {
        try {
            Thread.sleep(
                    ThreadLocalRandom.current().nextLong(5, 30)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while retrying",
                    e
            );
        }
    }
}