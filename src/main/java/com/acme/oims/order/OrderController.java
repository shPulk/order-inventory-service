package com.acme.oims.order;

import com.acme.oims.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record StatusChangeRequest(@NotNull OrderStatus target) {
    }

    /**
     * The customer is never in the body: it comes from the token.
     */
    public record PlaceOrderBody(@NotEmpty @Size(max = 50) @Valid List<CreateOrderRequest.Line> lines) {}

    private final OrderService service;
    private final OrderCancellationService cancellations;

    public OrderController(OrderService service, OrderCancellationService cancellations) {
        this.service = service;
        this.cancellations = cancellations;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody PlaceOrderBody body, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, Authentication auth) {
        var result = service.place(new CreateOrderRequest(auth.getName(), body.lines()), idempotencyKey);
        if (!result.created()) {
            return ResponseEntity.ok(result.order());
        }
        return ResponseEntity
                .created(URI.create("/api/orders/" + result.order().id()))
                .body(result.order());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Authentication auth) {
        return visibleTo(service.get(id), auth);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPS', 'ADMIN')")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        return service.transition(id, req.target());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, Authentication auth) {
        visibleTo(service.get(id), auth); // 404 if it is not yours
        return cancellations.cancel(id);
    }

    /**
     * Owner or staff. Anyone else gets 404, so order ids cannot be probed.
     */
    private OrderResponse visibleTo(OrderResponse order, Authentication auth) {
        boolean staff = auth.getAuthorities()
                .stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_WAREHOUSE_OPS") || a.getAuthority().equals("ROLE_ADMIN")
                );

        if (!staff && !order.customerId().equals(auth.getName())) {
            throw new NotFoundException("Order " + order.id() + " not found");
        }

        return order;
    }
}