package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalesFeedbackRepository extends JpaRepository<SalesFeedbackEntity, UUID> {
    Optional<SalesFeedbackEntity> findByFeedbackToken(String feedbackToken);
    List<SalesFeedbackEntity> findByCustomer_CustomerId(UUID customerId);
    List<SalesFeedbackEntity> findByBooking_BookingId(UUID bookingId);
}
