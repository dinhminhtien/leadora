package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.novax.leadora.infrastructure.persistence.repository.projection.StaffFeedbackPerformanceProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;

@Repository
public interface SalesFeedbackRepository extends JpaRepository<SalesFeedbackEntity, UUID>, JpaSpecificationExecutor<SalesFeedbackEntity> {
    Optional<SalesFeedbackEntity> findByFeedbackToken(String feedbackToken);
    List<SalesFeedbackEntity> findByCustomer_CustomerId(UUID customerId);
    List<SalesFeedbackEntity> findByBooking_BookingId(UUID bookingId);
    boolean existsByBooking_BookingIdAndSubmittedAtIsNotNull(UUID bookingId);

    @Query("SELECT f.salesStaff.userId as staffId, " +
           "COUNT(f) as totalFeedbacks, " +
           "SUM(CASE WHEN f.absaStatus = 'SUCCESS' THEN 1 ELSE 0 END) as totalAnalyzedFeedbacks, " +
           "SUM(CASE WHEN f.absaStatus = 'SUCCESS' AND (f.rating >= 4 OR (f.rating IS NULL AND UPPER(f.absaAttitudeSentiment) = 'POSITIVE')) THEN 1 ELSE 0 END) as positiveFeedbacks, " +
           "SUM(CASE WHEN f.absaStatus = 'SUCCESS' AND (f.rating = 3 OR (f.rating IS NULL AND UPPER(f.absaAttitudeSentiment) = 'NEUTRAL')) THEN 1 ELSE 0 END) as neutralFeedbacks, " +
           "SUM(CASE WHEN f.absaStatus = 'SUCCESS' AND (f.rating <= 2 OR (f.rating IS NULL AND UPPER(f.absaAttitudeSentiment) = 'NEGATIVE')) THEN 1 ELSE 0 END) as negativeFeedbacks, " +
           // Attitude
           "SUM(CASE WHEN UPPER(f.absaAttitudeSentiment) = 'POSITIVE' THEN 1 ELSE 0 END) as attitudePos, " +
           "SUM(CASE WHEN f.absaAttitudeSentiment IS NOT NULL THEN 1 ELSE 0 END) as attitudeCount, " +
           // Speed
           "SUM(CASE WHEN UPPER(f.absaSpeedSentiment) = 'POSITIVE' THEN 1 ELSE 0 END) as speedPos, " +
           "SUM(CASE WHEN f.absaSpeedSentiment IS NOT NULL THEN 1 ELSE 0 END) as speedCount, " +
           // Accuracy
           "SUM(CASE WHEN UPPER(f.absaAccuracySentiment) = 'POSITIVE' THEN 1 ELSE 0 END) as accuracyPos, " +
           "SUM(CASE WHEN f.absaAccuracySentiment IS NOT NULL THEN 1 ELSE 0 END) as accuracyCount, " +
           // Facility
           "SUM(CASE WHEN UPPER(f.absaFacilitySentiment) = 'POSITIVE' THEN 1 ELSE 0 END) as facilityPos, " +
           "SUM(CASE WHEN f.absaFacilitySentiment IS NOT NULL THEN 1 ELSE 0 END) as facilityCount, " +
           // Price
           "SUM(CASE WHEN UPPER(f.absaPriceSentiment) = 'POSITIVE' THEN 1 ELSE 0 END) as pricePos, " +
           "SUM(CASE WHEN f.absaPriceSentiment IS NOT NULL THEN 1 ELSE 0 END) as priceCount " +
           "FROM SalesFeedbackEntity f " +
           "WHERE f.submittedAt IS NOT NULL " +
           "AND f.salesStaff.userId IS NOT NULL " +
           "AND f.submittedAt >= :startDate " +
           "AND f.submittedAt <= :endDate " +
           "GROUP BY f.salesStaff.userId")
    List<StaffFeedbackPerformanceProjection> aggregateFeedbackPerformance(
            @Param("startDate") OffsetDateTime startDate, 
            @Param("endDate") OffsetDateTime endDate);
}

