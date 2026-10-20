package com.acme.oims.order;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.Warehouse;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.inventory.ReservationRepository;
import com.acme.oims.inventory.ReservationStatus;
import com.acme.oims.inventory.StockLevel;
import com.acme.oims.inventory.StockLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:oims_api_test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
public class ReservationFlowTest {

    @Autowired
    OrderService orderService;

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
        return products.save(new Product("T-" + UUID.randomUUID(), "Test", new BigDecimal("1.00")));
    }

    private StockLevel stockRow(Warehouse w, Product p, int onHand) {
        return stock.save(new StockLevel(w.getId(), p.getId(), onHand));
    }

    private CreateOrderRequest.Line line(Product p, int qty) {
        return new CreateOrderRequest.Line(p.getId(), qty);
    }

    private int reserved(StockLevel row) {
        return stock.findById(row.getId()).orElseThrow().getReserved();
    }

    @Test
    void successfulOrderIsReservedWithExpiry() {
        Warehouse w = warehouses.findAll().get(0);
        Product p = newProduct();
        StockLevel row = stockRow(w, p, 10);

        var result = orderService.place(
                new CreateOrderRequest("c1", List.of(line(p, 2))),
                null
        );

        assertEquals(OrderStatus.RESERVED, result.order().status());
        assertNotNull(result.order().expiresAt());
        assertEquals(2, reserved(row));
        assertEquals(
                1,
                reservations.countByStockLevelIdAndStatus(
                        row.getId(),
                        ReservationStatus.ACTIVE
                )
        );
    }

    @Test
    void multiLineOrderIsAllOrNothing() {
        Warehouse w = warehouses.findAll().get(0);
        Product a = newProduct();
        Product b = newProduct();

        StockLevel rowA = stockRow(w, a, 10);
        StockLevel rowB = stockRow(w, b, 1);

        var ex = assertThrows(
                InsufficientStockException.class,
                () -> orderService.place(
                        new CreateOrderRequest(
                                "c1",
                                List.of(line(a, 5), line(b, 2))
                        ),
                        null
                )
        );

        assertEquals(List.of(b.getId()), ex.getProductIds());
        assertEquals(
                0,
                reserved(rowA),
                "line A must not be reserved when line B fails"
        );
        assertEquals(0, reserved(rowB));

        assertEquals(
                OrderStatus.FAILED,
                orders.findById(ex.getOrderId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void allocationPicksWarehouseWithMostStock() {
        List<Warehouse> ws = warehouses.findAll();
        Product p = newProduct();

        StockLevel small = stockRow(ws.get(0), p, 5);
        StockLevel large = stockRow(ws.get(1), p, 8);

        orderService.place(
                new CreateOrderRequest(
                        "c1",
                        List.of(line(p, 3))
                ),
                null
        );

        assertEquals(0, reserved(small));
        assertEquals(3, reserved(large));
    }
}
