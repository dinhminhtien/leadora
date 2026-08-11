package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.FeedbackResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAspectDeepDiveFeedbackUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public Page<FeedbackResponse> execute(
            String aspect,
            String sentiment,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            int page,
            int size,
            String headerUserId
    ) {
        UserEntity actor = currentUserProvider.resolve(headerUserId);
        String roleName = actor.getRole() != null ? actor.getRole().getRoleName().trim().toUpperCase() : "";

        Specification<SalesFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Role Scoping
            if ("SALES".equalsIgnoreCase(roleName) || "SALES_STAFF".equalsIgnoreCase(roleName)) {
                predicates.add(cb.equal(root.get("salesStaff").get("userId"), actor.getUserId()));
            }

            // 2. Only submitted feedbacks
            predicates.add(cb.isNotNull(root.get("submittedAt")));

            // 3. Date Range
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), endDate));
            }

            // 4. Aspect Sentiment Filter
            if (StringUtils.hasText(aspect) && StringUtils.hasText(sentiment)) {
                String columnName = mapAspectToColumnName(aspect);
                predicates.add(cb.equal(root.get(columnName), sentiment));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Page<SalesFeedbackEntity> feedbackPage = salesFeedbackRepository.findAll(spec, pageable);

        return feedbackPage.map(this::mapToResponse);
    }

    private String mapAspectToColumnName(String aspect) {
        switch (aspect.toLowerCase().trim()) {
            case "attitude":
                return "absaAttitudeSentiment";
            case "speed":
                return "absaSpeedSentiment";
            case "accuracy":
                return "absaAccuracySentiment";
            case "facility":
                return "absaFacilitySentiment";
            case "price":
                return "absaPriceSentiment";
            default:
                throw new IllegalArgumentException("Unknown aspect: " + aspect);
        }
    }

    private FeedbackResponse mapToResponse(SalesFeedbackEntity entity) {
        String customerName = entity.getCustomer() != null ? entity.getCustomer().getFullName() : "N/A";
        String bookingCode = entity.getBooking() != null ? entity.getBooking().getBookingCode() : "N/A";
        String salesStaffName = entity.getSalesStaff() != null ? entity.getSalesStaff().getFullName() : "N/A";
        String reviewedByName = entity.getReviewedBy() != null ? entity.getReviewedBy().getFullName() : null;

        return FeedbackResponse.builder()
                .feedbackId(entity.getFeedbackId())
                .customerName(customerName)
                .bookingCode(bookingCode)
                .salesStaffName(salesStaffName)
                .rating(entity.getRating())
                .ratingAttitude(entity.getRatingAttitude())
                .ratingSpeed(entity.getRatingSpeed())
                .ratingAccuracy(entity.getRatingAccuracy())
                .comment(entity.getComment())
                .reviewStatus(entity.getReviewStatus())
                .submittedAt(entity.getSubmittedAt())
                .reviewedByName(reviewedByName)
                .reviewedAt(entity.getReviewedAt())
                .createdAt(entity.getCreatedAt())
                .absaAttitudeSentiment(entity.getAbsaAttitudeSentiment())
                .absaAttitudeConfidence(entity.getAbsaAttitudeConfidence())
                .absaSpeedSentiment(entity.getAbsaSpeedSentiment())
                .absaSpeedConfidence(entity.getAbsaSpeedConfidence())
                .absaAccuracySentiment(entity.getAbsaAccuracySentiment())
                .absaAccuracyConfidence(entity.getAbsaAccuracyConfidence())
                .absaFacilitySentiment(entity.getAbsaFacilitySentiment())
                .absaFacilityConfidence(entity.getAbsaFacilityConfidence())
                .absaPriceSentiment(entity.getAbsaPriceSentiment())
                .absaPriceConfidence(entity.getAbsaPriceConfidence())
                .absaStatus(entity.getAbsaStatus())
                .build();
    }
}
