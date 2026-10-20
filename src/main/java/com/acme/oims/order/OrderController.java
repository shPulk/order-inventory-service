package com.acme.oims.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record StatusChangeRequest(@NotNull OrderStatus target) {}

    private final OrderService service;
    private final OrderCancellationService cancellations;

    public OrderController(OrderService service, OrderCancellationService cancellations) {
        this.service = service;
        this.cancellations = cancellations;
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey) {

        var result = service.place(req, idempotencyKey);

        if (!result.created()) {
            return ResponseEntity.ok(result.order());
        }

        return ResponseEntity
                .created(URI.create("/api/orders/" + result.order().id()))
                .body(result.order());
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/status")
    OrderResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusChangeRequest req) {

        return service.transition(id, req.target());
    }

    @PostMapping("/{id}/cancel")
    OrderResponse cancel(@PathVariable Long id) {
        return cancellations.cancel(id);
    }
}