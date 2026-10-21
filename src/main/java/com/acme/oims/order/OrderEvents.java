package com.acme.oims.order;

import com.acme.oims.events.OrderEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class OrderEvents {

    private final ApplicationEventPublisher publisher;

    OrderEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** Call inside the transaction that changes the order. Delivered only after commit */
    void emit(String type, Order order, String reason) {
        publisher.publishEvent(OrderEvent.of(type, order.getId(), order.getCustomerId(), order.getStatus().name(), reason));
    }
}
