package com.acme.oims.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String customerId,
        @NotEmpty @Size(max = 50) @Valid List<Line> lines) {

    public record Line(@NotNull Long productId, @Min(1) @Max(10000) int quantity) {
    }
}
