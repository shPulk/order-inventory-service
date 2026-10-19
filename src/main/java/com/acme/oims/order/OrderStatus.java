package com.acme.oims.order;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    CREATED, RESERVED, PAID, SHIPPED, DELIVERED, CANCELLED, FAILED;

    private Set<OrderStatus> allowedNext() {
        return switch (this) {
            case CREATED -> EnumSet.of(RESERVED, FAILED, CANCELLED);
            case RESERVED -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED, FAILED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
