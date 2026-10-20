package com.acme.oims.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    public record AdjustStockRequest(@Min(0) int onHand) {}

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @GetMapping
    List<StockService.StockView> list(
            @RequestParam(required = false) Long productId) {
        return service.list(productId);
    }

    @PutMapping("/{warehouseId}/{productId}")
    StockService.StockView setOnHand(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @Valid @RequestBody AdjustStockRequest req) {

        return service.setOnHand(warehouseId, productId, req.onHand());
    }
}