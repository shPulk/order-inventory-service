package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.inventory.StockLevel;
import com.acme.oims.inventory.StockLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:idem_race;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "oims.events.async=false"
})
class IdempotencyRaceTest {

    @Autowired OrderService orderService;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired StockLevelRepository stocks;

    @Test
    void concurrentRetriesWithOneKeyNeverCreateTwoOrders() throws Exception {
        Product p = products.save(new Product("I-" + UUID.randomUUID(), "Item", new BigDecimal("1.00")));
        StockLevel row = stocks.save(new StockLevel(warehouses.findAll().getFirst().getId(), p.getId(), 100));
        String key = "race-" + UUID.randomUUID();
        var req = new CreateOrderRequest("c1", List.of(new CreateOrderRequest.Line(p.getId(), 2)));

        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<OrderService.PlacementResult>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return orderService.place(req, key);
            }));
        }

        ready.await();
        go.countDown();

        List<Throwable> errors = new ArrayList<>();
        List<OrderService.PlacementResult> results = new ArrayList<>();
        for (var f : futures) {
            try {
                results.add(f.get(60, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        pool.shutdown();

        long distinctOrders = results.stream().map(r -> r.order().id()).distinct().count();
        System.out.println("IDEMPOTENCY RACE -> succeeded=" + results.size() + " failed=" + errors.size()
                + " distinctOrders=" + distinctOrders + " errors="
                + errors.stream().map(t -> t.getClass().getSimpleName()).collect(Collectors.toSet()));

        // safety: the unique key must prevent a second order, and stock must be reserved once
        assertTrue(distinctOrders <= 1, "more than one order created for one key");
        assertEquals(2, stocks.findById(row.getId()).orElseThrow().getReserved(),
                "stock must be reserved exactly once");
        // liveness gap (known, see DRILLS.md): errors may be non-empty. Not asserted on purpose.
    }
}