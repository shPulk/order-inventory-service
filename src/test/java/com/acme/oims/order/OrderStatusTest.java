package com.acme.oims.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "CREATED,RESERVED", "CREATED,FAILED", "CREATED,CANCELLED",
            "RESERVED,PAID", "RESERVED,CANCELLED",
            "PAID,SHIPPED", "PAID,CANCELLED",
            "SHIPPED,DELIVERED"
    })
    void legalTransitions(OrderStatus from, OrderStatus to) {
        assertTrue(from.canTransitionTo(to));
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED,PAID", "CREATED,SHIPPED", "RESERVED,SHIPPED",
            "PAID,RESERVED", "SHIPPED,CANCELLED", "DELIVERED,CANCELLED",
            "CANCELLED,RESERVED", "FAILED,CREATED"
    })
    void illegalTransitions(OrderStatus from, OrderStatus to) {
        assertFalse(from.canTransitionTo(to));
    }

    @Test
    void orderRejectsIllegalTransition() {
        Order o = new Order("customer-1", null);
        assertThrows(
                IllegalTransitionException.class,
                () -> o.transitionTo(OrderStatus.SHIPPED)
        );
    }

    @Test
    void terminalStates() {
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertTrue(OrderStatus.FAILED.isTerminal());
        assertFalse(OrderStatus.PAID.isTerminal());
    }
}