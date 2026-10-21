package com.acme.oims.events;

import java.time.Instant;
import java.util.UUID;

public record OrderEvent(String eventId, String type, Long orderId, String customerId, String status, String reason, Instant occurredAt) {

    public static OrderEvent of(String type, Long orderId, String customerId, String status, String reason) {
       return new OrderEvent(UUID.randomUUID().toString(), type, orderId, customerId, status, reason, Instant.now());
    }
}
