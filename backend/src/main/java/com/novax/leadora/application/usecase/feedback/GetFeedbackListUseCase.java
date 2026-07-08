package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.FeedbackResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetFeedbackListUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public Page<FeedbackResponse> execute(
            String search,
            ReviewStatus reviewStatus,
            Short rating,
            int page,
            int size,
            String headerUserId
    ) {
        UserEntity actor = currentUserProvider.resolve(headerUserId);
        String roleName = actor.getRole() != null ? actor.getRole().getRoleName().trim().toUpperCase() : "";

        Specification<SalesFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Role-based Scoping: SALES can only see their own feedback
            if ("SALES".equalsIgnoreCase(roleName) || "SALES_STAFF".equalsIgnoreCase(roleName)) {
                predicates.add(cb.equal(root.get("salesStaff").get("userId"), actor.getUserId()));
            }

            // 2. Filter by Review Status
            if (reviewStatus != null) {
                predicates.add(cb.equal(root.get("reviewStatus"), reviewStatus));
            }

            // 3. Filter by Rating
            if (rating != null) {
                predicates.add(cb.equal(root.get("rating"), rating));
            }

            // 4. Keywords Search (Customer Name, Sales Representative, or Comment)
            if (StringUtils.hasText(search)) {
                String likePattern = "%" + search.trim().toLowerCase() + "%";
                Join<Object, Object> customerJoin = root.join("customer", JoinType.LEFT);
                Join<Object, Object> salesStaffJoin = root.join("salesStaff", JoinType.LEFT);

                Predicate customerNamePred = cb.like(cb.lower(customerJoin.get("fullName")), likePattern);
                Predicate staffNamePred = cb.like(cb.lower(salesStaffJoin.get("fullName")), likePattern);
                Predicate commentPred = cb.like(cb.lower(root.get("comment")), likePattern);

                predicates.add(cb.or(customerNamePred, staffNamePred, commentPred));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SalesFeedbackEntity> feedbackPage = salesFeedbackRepository.findAll(spec, pageable);

        return feedbackPage.map(this::mapToResponse);
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
                .comment(entity.getComment())
                .reviewStatus(entity.getReviewStatus())
                .submittedAt(entity.getSubmittedAt())
                .reviewedByName(reviewedByName)
                .reviewedAt(entity.getReviewedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
