package com.acme.oims;

import com.acme.oims.catalog.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:oims_api_test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductRepository products;

    private Long productId() {
        return products.findBySku("SKU-001").orElseThrow().getId();
    }

    private String body(Long productId, int qty) {
        return """
                {"customerId":"cust-1","lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(productId, qty);
    }

    @Test
    void placeOrderWithIdempotency() throws Exception {
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "create-7")
                        .content(body(productId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(19.98));
    }

    @Test
    void emptyLinesReturns400() throws Exception {
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"c","lines":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lines").exists());
    }

    @Test
    void unknownProductReturns422() throws Exception {
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999L, 1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateLineReturns422() throws Exception {
        long p = productId();

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"customerId":"c","lines":[
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
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId(), 3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer id = JsonPath.read(first, "$.id");

        mvc.perform(post("/api/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId(), 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        String created = mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId(), 1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer id = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"PAID\"}")) // CREATED -> PAID is illegal
                .andExpect(status().isConflict());
    }

    @Test
    void missingOrderReturns404() throws Exception {
        mvc.perform(get("/api/orders/987654"))
                .andExpect(status().isNotFound());
    }
}