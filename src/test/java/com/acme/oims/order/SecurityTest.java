package com.acme.oims.order;

import com.acme.oims.catalog.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:oims_security_test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductRepository products;

    private static RequestPostProcessor as(String subject, String... roles) {
        GrantedAuthority[] auths = Arrays.stream(roles)
                .map(r -> (GrantedAuthority)
                        new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(GrantedAuthority[]::new);

        return jwt().jwt(j -> j.subject(subject))
                .authorities(auths);
    }

    private String orderBody() {
        long p = products.findBySku("SKU-001")
                .orElseThrow()
                .getId();

        return "{\"lines\":[{\"productId\":" + p + ",\"quantity\":1}]}";
    }

    private int placeAs(String subject) throws Exception {
        String json = mvc.perform(post("/api/orders")
                        .with(as(subject, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(json, "$.id");
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenGets401() throws Exception {
        mvc.perform(get("/api/orders/1")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerIdComesFromTheToken() throws Exception {
        String json = mvc.perform(post("/api/orders")
                        .with(as("alice", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()
                                .replace(
                                        "{\"lines\":",
                                        "{\"customerId\":\"mallory\",\"lines\":"
                                )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("alice")) // body value ignored
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonPath.read(json, "$.id");
    }

    @Test
    void otherCustomerCannotReadOrCancel() throws Exception {
        int id = placeAs("alice");

        mvc.perform(get("/api/orders/" + id)
                        .with(as("bob", "CUSTOMER")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/orders/" + id + "/cancel")
                        .with(as("bob", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerAndStaffCanRead() throws Exception {
        int id = placeAs("alice");

        mvc.perform(get("/api/orders/" + id)
                        .with(as("alice", "CUSTOMER")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/orders/" + id)
                        .with(as("ops-1", "WAREHOUSE_OPS")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/orders/" + id)
                        .with(as("root", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotChangeStatusOrStock() throws Exception {
        int id = placeAs("alice");

        mvc.perform(post("/api/orders/" + id + "/status")
                        .with(as("alice", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"PAID\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/stock/1/1")
                        .with(as("alice", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onHand\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsCanMoveReservedOrderToPaid() throws Exception {
        int id = placeAs("alice");

        mvc.perform(post("/api/orders/" + id + "/status")
                        .with(as("ops-1", "WAREHOUSE_OPS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void realSignedTokenRoundTrip() throws Exception {
        String json = mvc.perform(post("/api/dev/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"carol","roles":["CUSTOMER"]}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(json, "$.token");

        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("carol"));
    }

    @Test
    void tamperedTokenGets401() throws Exception {
        String json = mvc.perform(post("/api/dev/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"carol","roles":["ADMIN"]}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(json, "$.token");
        String tampered =
                token.substring(0, token.length() - 3) + "abc";

        mvc.perform(get("/api/orders/1")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}