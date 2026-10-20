package com.acme.oims.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    List<StockLevel> findByProductId(Long productId);

    Optional<StockLevel> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.productId in :productIds order by s.id")
    List<StockLevel> lockByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("select s from StockLevel s where s.productId in :productIds order by s.id")
    List<StockLevel> findAllByProductIds(@Param("productIds") Collection<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.id in :ids order by s.id")
    List<StockLevel> lockByIds(@Param("ids") Collection<Long> ids);
}
