package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.Warehouse;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.inventory.ReservationRepository;
import com.acme.oims.inventory.ReservationStatus;
import com.acme.oims.inventory.StockLevel;
import com.acme.oims.inventory.StockLevelRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractConcurrencyTest {

    static final int THREADS = 100;
    static final int STOCK = 10;

    record Outcome(int reserved, int rejected, int failed, int stockReserved, long activeReservations, long elapsedMs) {}

    @Autowired
    OrderService orderService;

    @Autowired
    ProductRepository products;

    @Autowired
    WarehouseRepository warehouses;

    @Autowired
    StockLevelRepository stockRepo;

    @Autowired
    ReservationRepository reservations;


    /** 100 threads each try to reserve 1 unit of a product that has 10 in stock. */
    Outcome race() throws Exception {
        Warehouse w = warehouses.findAll().getFirst();
        Product p = products.save(new Product("C-" + UUID.randomUUID(), "Contended", new BigDecimal("1.00")));
        StockLevel row = stockRepo.save(new StockLevel(w.getId(), p.getId(), STOCK));

        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    orderService.place(new CreateOrderRequest("c-"+ n,
                            List.of(new CreateOrderRequest.Line(p.getId(),1))),null);
                    reserved.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        long t0 = System.nanoTime();
        go.countDown();
        for (Future<Object> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();

        StockLevel finalRow = stockRepo.findById(row.getId()).orElseThrow();
        long active = reservations.countByStockLevelIdAndStatus(row.getId(), ReservationStatus.ACTIVE);
        return new Outcome(reserved.get(), rejected.get(), failed.get(), finalRow.getReserved(), active, elapsedMs);
    }

    void assertNeverOverSold(Outcome o) {
        assertEquals(THREADS, o.reserved() + o.rejected() + o.failed(), "every request has an outcome");
        assertTrue(o.stockReserved() <= STOCK, "reserved must not exceed on_hand");
        assertTrue(STOCK - o.stockReserved() >= 0, "available must not go negative");
        assertEquals(o.reserved(), o.stockReserved(), "successful orders == units reserved");
        assertEquals(o.reserved(), o.activeReservations(), "successful orders == reservation rows");
    }
}
