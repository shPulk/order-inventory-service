package com.acme.oims.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from com.acme.oims.order.Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("select o.id from com.acme.oims.order.Order o "
            + "where o.status = :status and o.expiresAt < :now order by o.expiresAt")
    List<Long> findExpiredIds(@Param("status") OrderStatus status, @Param("now")Instant now, Pageable page);
}
