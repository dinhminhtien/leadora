package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sales_feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesFeedbackEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "feedback_id")
    private UUID feedbackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_staff_id")
    private UserEntity salesStaff;

    @Column(name = "rating")
    private Short rating;

    @Column(name = "rating_attitude")
    private Short ratingAttitude;

    @Column(name = "rating_speed")
    private Short ratingSpeed;

    @Column(name = "rating_accuracy")
    private Short ratingAccuracy;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "feedback_token", unique = true, length = 255)
    private String feedbackToken;

    @Column(name = "token_expires_at")
    private OffsetDateTime tokenExpiresAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserEntity reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "absa_attitude_sentiment", length = 20)
    private String absaAttitudeSentiment;

    @Column(name = "absa_attitude_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal absaAttitudeConfidence;

    @Column(name = "absa_speed_sentiment", length = 20)
    private String absaSpeedSentiment;

    @Column(name = "absa_speed_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal absaSpeedConfidence;

    @Column(name = "absa_accuracy_sentiment", length = 20)
    private String absaAccuracySentiment;

    @Column(name = "absa_accuracy_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal absaAccuracyConfidence;

    @Column(name = "absa_facility_sentiment", length = 20)
    private String absaFacilitySentiment;

    @Column(name = "absa_facility_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal absaFacilityConfidence;

    @Column(name = "absa_price_sentiment", length = 20)
    private String absaPriceSentiment;

    @Column(name = "absa_price_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal absaPriceConfidence;

    @Column(name = "absa_status", length = 20)
    private String absaStatus;
}

