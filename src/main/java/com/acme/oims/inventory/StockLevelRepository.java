package com.acme.oims.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    List<StockLevel> findByProductId(Long productId);

    Optional<StockLevel> findByWarehouseIdAndProductId(Long warehouseId, Long productId);
}
