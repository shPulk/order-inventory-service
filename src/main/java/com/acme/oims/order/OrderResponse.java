package com.acme.oims.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerId,
        OrderStatus status,
        Instant expiresAt,
        Instant createdAt,
        BigDecimal total,
        List<LineView> lines
) {

    public record LineView(
            Long productId,
            int quantity,
            BigDecimal unitPrice
    ) {}

    public static OrderResponse from(Order o) {
        List<LineView> lines = o.getLines().stream()
                .map(l -> new LineView(
                        l.getProductId(),
                        l.getQuantity(),
                        l.getUnitPrice()
                ))
                .toList();

        BigDecimal total = lines.stream()
                .map(l -> l.unitPrice()
                        .multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderResponse(
                o.getId(),
                o.getCustomerId(),
                o.getStatus(),
                o.getExpiresAt(),
                o.getCreatedAt(),
                total,
                lines
        );
    }
}