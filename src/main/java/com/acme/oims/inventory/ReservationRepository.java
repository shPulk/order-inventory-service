package com.acme.oims.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    long countByStockLevelIdAndStatus(Long stockLevelId, ReservationStatus status);
}
