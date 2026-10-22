package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.events.NotificationListener;
import com.acme.oims.inventory.StockLevel;
import com.acme.oims.inventory.StockLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:slow_downstream;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "oims.events.simulated-delay-ms=500"
})
class SlowDownstreamTest {

    @Autowired OrderService orderService;
    @Autowired NotificationListener listeners;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired StockLevelRepository stocks;

    @Test
    void slowListenerMustNotSlowTheRequest() throws Exception {
        Product p = products.save(new Product("S-" + UUID.randomUUID(), "Slow", new BigDecimal("1.00")));
        stocks.save(new StockLevel(warehouses.findAll().getFirst().getId(), p.getId(), 100));
        var req = new CreateOrderRequest("c1", List.of(new CreateOrderRequest.Line(p.getId(), 1)));
        orderService.place(req, null);                         // warm-up

        long t0 = System.nanoTime();
        long id = orderService.place(req, null).order().id();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("SLOW DOWNSTREAM -> place() took " + ms + " ms");

        assertTrue(ms < 250, "request waited for the listener: " + ms + " ms");

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline
                && listeners.received().stream().noneMatch(e -> e.orderId().equals(id))) {
            Thread.sleep(50);
        }

        assertTrue(listeners.received().stream().anyMatch(e -> e.orderId().equals(id)),
                "event must still be delivered, just later");
    }
}