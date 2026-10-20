package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.inventory.ReservationRepository;
import com.acme.oims.inventory.ReservationStatus;
import com.acme.oims.inventory.StockLevel;
import com.acme.oims.inventory.StockLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:oims_cancel_test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class CancellationAndExpiryTest {

    @Autowired
    OrderService orderService;

    @Autowired
    OrderCancellationService cancellations;

    @Autowired
    ReservationExpiryJob expiryJob;

    @Autowired
    ProductRepository products;

    @Autowired
    WarehouseRepository warehouses;

    @Autowired
    StockLevelRepository stock;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    OrderRepository orders;

    private Product newProduct() {
        return products.save(
                new Product(
                        "X-" + UUID.randomUUID(),
                        "Test",
                        new BigDecimal("1.00")
                )
        );
    }

    private StockLevel row(Product p, int onHand) {
        Long warehouseId = warehouses.findAll().getFirst().getId();

        return stock.save(
                new StockLevel(
                        warehouseId,
                        p.getId(),
                        onHand
                )
        );
    }

    private Long place(Product p, int qty) {
        return orderService.place(
                new CreateOrderRequest(
                        "c1",
                        List.of(
                                new CreateOrderRequest.Line(p.getId(), qty)
                        )
                ),
                null
        ).order().id();
    }

    private int reserved(StockLevel r) {
        return stock.findById(r.getId())
                .orElseThrow()
                .getReserved();
    }

    private long released(StockLevel r) {
        return reservations.countByStockLevelIdAndStatus(
                r.getId(),
                ReservationStatus.RELEASED
        );
    }

    private List<Throwable> runConcurrently(
            int n,
            Callable<Object> task
    ) throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }

        ready.await();
        go.countDown();

        List<Throwable> errors = new ArrayList<>();

        for (Future<Object> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }

        pool.shutdown();
        return errors;
    }

    @Test
    void cancelReleasesStock() {
        Product p = newProduct();
        StockLevel r = row(p, 10);

        Long id = place(p, 4);
        assertEquals(4, reserved(r));

        OrderResponse resp = cancellations.cancel(id);

        assertEquals(OrderStatus.CANCELLED, resp.status());
        assertEquals(0, reserved(r));
        assertEquals(1, released(r));
    }

    @Test
    void cancelTwiceIsIdempotent() {
        Product p = newProduct();
        StockLevel r = row(p, 10);

        Long id = place(p, 3);

        cancellations.cancel(id);
        OrderResponse second = cancellations.cancel(id); // must not throw, must not release again

        assertEquals(OrderStatus.CANCELLED, second.status());
        assertEquals(0, reserved(r));
        assertEquals(1, released(r));
    }

    @Test
    void concurrentCancelsReleaseExactlyOnce() throws Exception {
        Product p = newProduct();
        StockLevel r = row(p, 10);

        place(p, 2);                     // another live order keeps 2 reserved
        Long id = place(p, 5);

        assertEquals(7, reserved(r));

        List<Throwable> errors =
                runConcurrently(10, () -> cancellations.cancel(id));

        assertTrue(
                errors.isEmpty(),
                "no cancel call may fail: " + errors
        );

        assertEquals(
                2,
                reserved(r),
                "only the cancelled order's 5 units are released, once"
        );

        assertEquals(1, released(r));
    }

    @Test
    void expiryCancelsUnpaidOrderAndReleasesStock() {
        Product p = newProduct();
        StockLevel r = row(p, 10);
        Long id = place(p, 6);

        // treat "now" as one hour ahead instead of sleeping for the TTL
        expiryJob.expireDue(
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertEquals(
                OrderStatus.CANCELLED,
                orders.findById(id).orElseThrow().getStatus()
        );
        assertEquals(0, reserved(r));
    }

    @Test
    void expiryRunningOnTwoInstancesReleasesEachOrderOnce() throws Exception {
        Product p = newProduct();
        StockLevel r = row(p, 20);

        for (int i = 0; i < 5; i++) {
            place(p, 1);
        }

        assertEquals(5, reserved(r));

        Instant later = Instant.now().plus(1, ChronoUnit.HOURS);

        // two "instances" sweeping at the same moment against the same database
        List<Throwable> errors =
                runConcurrently(2, () -> expiryJob.expireDue(later));

        assertTrue(
                errors.isEmpty(),
                "sweeps must not fail: " + errors
        );
        assertEquals(
                0,
                reserved(r),
                "stock fully released, never negative"
        );
        assertEquals(
                5,
                released(r),
                "each reservation released exactly once"
        );
    }

    @Test
    void paidOrderIsNotExpired() {
        Product p = newProduct();
        StockLevel r = row(p, 10);
        Long id = place(p, 2);

        orderService.transition(id, OrderStatus.PAID);

        expiryJob.expireDue(
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertEquals(
                OrderStatus.PAID,
                orders.findById(id).orElseThrow().getStatus()
        );
        assertEquals(2, reserved(r));
    }
}