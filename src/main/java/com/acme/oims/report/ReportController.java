package com.acme.oims.report;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('WAREHOUSE_OPS', 'ADMIN')")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** from/to are ISO-8601 instants, e.g. 2026-10-01T00:00:00Z. Default: last 30 days. */
    @GetMapping("/top-products")
    public List<TopProductRow> topProducts(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "10") int limit) {

        return service.topProducts(from, to, limit);
    }

    @GetMapping("/low-stock")
    public List<LowStockRow> lowStock(
            @RequestParam(defaultValue = "5") int threshold,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "50") int limit) {

        return service.lowStock(threshold, warehouseId, limit);
    }
}