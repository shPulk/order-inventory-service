package com.acme.oims.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationListener.class);

    private final long simulatedDelayMs;

    private final List<OrderEvent> received =
            new CopyOnWriteArrayList<>();

    public NotificationListener(
            @Value("${oims.events.simulated-delay-ms:0}") long simulatedDelayMs
    ) {
        this.simulatedDelayMs = simulatedDelayMs;
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderEvent event) {
        if (simulatedDelayMs > 0) { // drill only: simulates a slow downstream
            try {
                Thread.sleep(simulatedDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info(
                "[notifications] {} for order {} on thread {}",
                event.type(),
                event.orderId(),
                Thread.currentThread().getName()
        );

        received.add(event);
    }

    public List<OrderEvent> received() {
        return received;
    }
}