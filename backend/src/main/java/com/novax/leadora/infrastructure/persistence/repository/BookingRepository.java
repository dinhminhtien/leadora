package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {
    Optional<BookingEntity> findByBookingCode(String bookingCode);

    List<BookingEntity> findByCustomer_CustomerId(UUID customerId);

    List<BookingEntity> findByStatus(BookingStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM BookingEntity b LEFT JOIN b.customer c " +
           "WHERE (:status IS NULL OR b.status = :status) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<BookingEntity> searchBookings(
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("status") BookingStatus status,
            org.springframework.data.domain.Pageable pageable);
}
