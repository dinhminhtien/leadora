package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, UUID>, JpaSpecificationExecutor<BookingEntity> {
    Optional<BookingEntity> findByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    List<BookingEntity> findByCustomer_CustomerId(UUID customerId);

    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    List<BookingEntity> findByStatus(BookingStatus status);

    @Override
    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    Page<BookingEntity> findAll(Specification<BookingEntity> spec, Pageable pageable);
}
