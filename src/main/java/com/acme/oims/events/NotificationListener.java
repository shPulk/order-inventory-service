package com.acme.oims.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    /** Kept so tests can assert what was delivered. Not for production use. */
    private final List<OrderEvent> received = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderEvent event) {
        log.info("[notifications] {} for order {} (status {})", event.type(), event.orderId(), event.status());
        received.add(event);
    }

    public List<OrderEvent> received() {
        return received;
    }
}
