package com.acme.oims.report;

import com.acme.oims.catalog.Product;
import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.inventory.StockLevel;
import com.acme.oims.inventory.StockLevelRepository;
import com.acme.oims.order.CreateOrderRequest;
import com.acme.oims.order.OrderCancellationService;
import com.acme.oims.order.OrderService;
import com.acme.oims.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:oims_report_test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
class ReportApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    OrderService orderService;

    @Autowired
    OrderCancellationService cancellations;

    @Autowired
    ProductRepository products;

    @Autowired
    WarehouseRepository warehouses;

    @Autowired
    StockLevelRepository stock;

    private RequestPostProcessor ops() {
        return jwt()
                .jwt(j -> j.subject("ops-1"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_WAREHOUSE_OPS")
                );
    }

    private RequestPostProcessor customer() {
        return jwt()
                .jwt(j -> j.subject("cust-1"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                );
    }

    private Product productWithStock(int onHand) {
        Product p = products.save(
                new Product(
                        "R-" + UUID.randomUUID(),
                        "Report test",
                        new BigDecimal("2.00")
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
                                new CreateOrderRequest.Line(
                                        p.getId(),
                                        qty
                                )
                        )
                ),
                null
        ).order().id();
    }

    @Test
    void topProductsCountsOnlyPaidOrders() throws Exception {
        Product p = productWithStock(100);

        orderService.transition(
                place(p, 3),
                OrderStatus.PAID
        );                                      // counts

        cancellations.cancel(place(p, 5));    // cancelled: ignored
        place(p, 2);                           // reserved, unpaid: ignored

        mvc.perform(get("/api/reports/top-products")
                        .param("limit", "100")
                        .with(ops()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.sku=='" + p.getSku() + "')].units"
                ).value(3))
                .andExpect(jsonPath(
                        "$[?(@.sku=='" + p.getSku() + "')].revenue"
                ).value(6.00))
                .andExpect(jsonPath(
                        "$[?(@.sku=='" + p.getSku() + "')].orders"
                ).value(1));
    }

    @Test
    void topProductsRejectsInvertedRange() throws Exception {
        mvc.perform(get("/api/reports/top-products")
                        .param("from", "2026-10-02T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z")
                        .with(ops()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void lowStockListsOnlyProductsAtOrBelowThreshold() throws Exception {
        Product low = productWithStock(10);
        Product plenty = productWithStock(100);

        place(low, 8); // available = 2

        mvc.perform(get("/api/reports/low-stock")
                        .param("threshold", "5")
                        .param("limit", "100")
                        .with(ops()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.sku=='" + low.getSku() + "')].available"
                ).value(2))
                .andExpect(jsonPath(
                        "$[?(@.sku=='" + plenty.getSku() + "')]"
                ).isEmpty());
    }

    @Test
    void customersCannotReadReports() throws Exception {
        mvc.perform(get("/api/reports/top-products")
                        .with(customer()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reports/low-stock")
                        .with(customer()))
                .andExpect(status().isForbidden());
    }
}