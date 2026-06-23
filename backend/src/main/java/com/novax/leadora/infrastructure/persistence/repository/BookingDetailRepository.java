package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingDetailRepository extends JpaRepository<BookingDetailEntity, UUID> {
    
    @EntityGraph(attributePaths = {"productService"})
    List<BookingDetailEntity> findByBooking_BookingId(UUID bookingId);

    @EntityGraph(attributePaths = {"productService"})
    List<BookingDetailEntity> findByBooking_BookingIdIn(Collection<UUID> bookingIds);
}
