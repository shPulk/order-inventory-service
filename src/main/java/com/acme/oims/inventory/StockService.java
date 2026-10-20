package com.acme.oims.inventory;

import com.acme.oims.catalog.ProductRepository;
import com.acme.oims.catalog.WarehouseRepository;
import com.acme.oims.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockService {

    public record StockView(
            Long id,
            Long warehouseId,
            Long productId,
            int onHand,
            int reserved,
            int available
    ) {
        static StockView from(StockLevel s) {
            return new StockView(
                    s.getId(),
                    s.getWarehouseId(),
                    s.getProductId(),
                    s.getOnHand(),
                    s.getReserved(),
                    s.available()
            );
        }
    }

    private final StockLevelRepository stock;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;

    public StockService(
            StockLevelRepository stock,
            ProductRepository products,
            WarehouseRepository warehouses
    ) {
        this.stock = stock;
        this.products = products;
        this.warehouses = warehouses;
    }

    @Transactional(readOnly = true)
    public List<StockView> list(Long productId) {
        List<StockLevel> rows = productId == null
                ? stock.findAll()
                : stock.findByProductId(productId);

        return rows.stream()
                .map(StockView::from)
                .toList();
    }

    @Transactional
    public StockView setOnHand(Long warehouseId, Long productId, int onHand) {
        if (!warehouses.existsById(warehouseId)) {
            throw new NotFoundException(
                    "Warehouse " + warehouseId + " not found"
            );
        }

        if (!products.existsById(productId)) {
            throw new NotFoundException(
                    "Product " + productId + " not found"
            );
        }

        StockLevel level = stock
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseGet(() -> new StockLevel(warehouseId, productId, 0));

        level.setOnHand(onHand);

        return StockView.from(stock.save(level));
    }
}