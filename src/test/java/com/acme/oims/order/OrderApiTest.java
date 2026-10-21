package com.acme.oims.order;

import com.acme.oims.catalog.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:oims_api_test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductRepository products;

    private RequestPostProcessor customer() {
        return jwt().jwt(j -> j.subject("cust-1"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                );
    }

    private RequestPostProcessor ops() {
        return jwt().jwt(j -> j.subject("ops-1"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_WAREHOUSE_OPS")
                );
    }

    private long productId() {
        return products.findBySku("SKU-001")
                .orElseThrow()
                .getId();
    }

    private String body(long productId, int qty) {
        return """
                {"lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(productId, qty);
    }

    @Test
    void placesOrderWithPriceSnapshot() throws Exception {
        mvc.perform(post("/api/orders")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.customerId").value("cust-1"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(9.99))
                .andExpect(jsonPath("$.total").value(19.98));
    }

    @Test
    void emptyLinesReturns400() throws Exception {
        mvc.perform(post("/api/orders")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lines").exists());
    }

    @Test
    void unknownProductReturns422() throws Exception {
        mvc.perform(post("/api/orders")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999, 1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateLineReturns422() throws Exception {
        long p = productId();

        mvc.perform(post("/api/orders")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[
                                    {"productId":%d,"quantity":1},
                                    {"productId":%d,"quantity":2}
                                ]}
                                """.formatted(p, p)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sameIdempotencyKeyReturnsSameOrder() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = mvc.perform(post("/api/orders")
                        .with(customer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId(), 1)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer id = JsonPath.read(first, "$.id");

        mvc.perform(post("/api/orders")
                        .with(customer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId(), 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        String created = mvc.perform(post("/api/orders")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId(), 1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer id = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/orders/" + id + "/status")
                        .with(ops())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"SHIPPED\"}")) // RESERVED -> SHIPPED is illegal
                .andExpect(status().isConflict());
    }

    @Test
    void missingOrderReturns404() throws Exception {
        mvc.perform(get("/api/orders/987654").with(customer())).andExpect(status().isNotFound());
    }
}