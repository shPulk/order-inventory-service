package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.common.BusinessRuleException;
import com.acme.oims.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    public record PlacementResult(OrderResponse order, boolean created) {}

    private final OrderRepository orders;
    private final ProductRepository products;

    public OrderService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    @Transactional
    public PlacementResult place(CreateOrderRequest req, String idempotencyKey) {
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
                        false
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
            // price snapshot; later catalog price changes must not alter existing orders
            order.addLine(
                    line.productId(),
                    line.quantity(),
                    byId.get(line.productId()).getPrice()
            );
        }

        return new PlacementResult(
                OrderResponse.from(orders.save(order)),
                true
        );
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(find(id));
    }

    @Transactional
    public OrderResponse transition(Long id, OrderStatus target) {
        if (target == OrderStatus.CANCELLED) {
            // cancellation must release stock, so it gets its own flow (Day 2)
            throw new BusinessRuleException(
                    "Use the cancel endpoint to cancel an order"
            );
        }

        Order order = find(id);
        order.transitionTo(target); // throws IllegalTransitionException -> 409

        return OrderResponse.from(order);
    }

    private Order find(Long id) {
        return orders.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Order " + id + " not found"));
    }

}