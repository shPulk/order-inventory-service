package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.events.NotificationListener;
import com.acme.oims.events.OrderEvent;
import com.acme.oims.inventory.StockLevel;
import com.acme.oims.inventory.StockLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oims_events_test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "oims.events.async=false"})
class EventsTest {

    @Autowired
    OrderService orderService;

    @Autowired
    OrderCancellationService cancellations;

    @Autowired
    NotificationListener listener;

    @Autowired
    ProductRepository products;

    @Autowired
    WarehouseRepository warehouses;

    @Autowired
    StockLevelRepository stock;

    private Product productWithStock(int onHand) {
        Product p = products.save(
                new Product(
                        "E-" + UUID.randomUUID(),
                        "Test",
                        new BigDecimal("1.00")
                )
        );

        stock.save(
                new StockLevel(
                        warehouses.findAll().getFirst().getId(),
                        p.getId(),
                        onHand
                )
        );

        return p;
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

    private List<String> typesFor(Long orderId) {
        return listener.received()
                .stream()
                .filter(e -> e.orderId().equals(orderId))
                .map(OrderEvent::type)
                .toList();
    }

    @Test
    void placingAnOrderEmitsOrderReservedAfterCommit() {
        Long id = place(productWithStock(10), 2);

        assertEquals(
                List.of("OrderReserved"),
                typesFor(id)
        );
    }

    @Test
    void outOfStockEmitsOrderFailed() {
        Product p = productWithStock(1);

        var ex = assertThrows(
                InsufficientStockException.class,
                () -> place(p, 5)
        );

        assertEquals(
                List.of("OrderFailed"),
                typesFor(ex.getOrderId())
        );
    }

    @Test
    void payingEmitsOrderPaid() {
        Long id = place(productWithStock(10), 1);

        orderService.transition(id, OrderStatus.PAID);

        assertEquals(
                List.of("OrderReserved", "OrderPaid"),
                typesFor(id)
        );
    }

    @Test
    void cancellingTwiceEmitsOneCancelledEvent() {
        Long id = place(productWithStock(10), 2);

        cancellations.cancel(id);
        cancellations.cancel(id);

        assertEquals(
                List.of("OrderReserved", "OrderCancelled"),
                typesFor(id)
        );
    }

    @Test
    void rolledBackTransactionEmitsNothing() {
        Long id = place(productWithStock(10), 1);
        int before = typesFor(id).size();

        // illegal transition: the transaction rolls back, so no event may be delivered
        assertThrows(
                IllegalTransitionException.class,
                () -> orderService.transition(id, OrderStatus.SHIPPED)
        );

        assertEquals(before, typesFor(id).size());
    }
}