package com.acme.oims.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;


@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);
    private static final int BATCH = 100;

    private final OrderRepository orders;
    private final OrderCancellationService cancellations;

    public ReservationExpiryJob(OrderRepository orders, OrderCancellationService cancellations) {
        this.orders = orders;
        this.cancellations = cancellations;
    }

    @Scheduled(fixedDelayString = "${oims.reservation.expiry-check-ms:30000}")
    public void run() {
        expireDue(Instant.now());
    }

    /** One batch per call, leftovers are picked up on the next. Safe to run concurrently. */
    public int expireDue(Instant now) {
        List<Long> ids = orders.findExpiredIds(OrderStatus.RESERVED, now, PageRequest.of(0, BATCH));
        int expired = 0;
        for (Long id : ids) {
            try {
                if (cancellations.expire(id, now)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}: {}", id, e.toString());   //others still proceed
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid order(s) and released their stock", expired);
        }
        return expired;
    }
}
