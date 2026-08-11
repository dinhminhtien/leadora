package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.SentimentOverviewResponse;
import com.novax.leadora.api.dto.response.SentimentOverviewResponse.AspectSentimentSummary;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSentimentAnalyticsUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public SentimentOverviewResponse execute(
            OffsetDateTime startDate,
            OffsetDateTime endDate,
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

            // 2. Filter only submitted feedbacks
            predicates.add(cb.isNotNull(root.get("submittedAt")));

            // 3. Date Range
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<SalesFeedbackEntity> feedbacks = salesFeedbackRepository.findAll(spec);

        // Aspect counters
        AspectCounter attitudeCounter = new AspectCounter();
        AspectCounter speedCounter = new AspectCounter();
        AspectCounter accuracyCounter = new AspectCounter();
        AspectCounter facilityCounter = new AspectCounter();
        AspectCounter priceCounter = new AspectCounter();

        for (SalesFeedbackEntity f : feedbacks) {
            attitudeCounter.accumulate(f.getAbsaAttitudeSentiment());
            speedCounter.accumulate(f.getAbsaSpeedSentiment());
            accuracyCounter.accumulate(f.getAbsaAccuracySentiment());
            facilityCounter.accumulate(f.getAbsaFacilitySentiment());
            priceCounter.accumulate(f.getAbsaPriceSentiment());
        }

        return SentimentOverviewResponse.builder()
                .attitude(attitudeCounter.toSummary())
                .speed(speedCounter.toSummary())
                .accuracy(accuracyCounter.toSummary())
                .facility(facilityCounter.toSummary())
                .price(priceCounter.toSummary())
                .build();
    }

    private static class AspectCounter {
        private long positive = 0;
        private long neutral = 0;
        private long negative = 0;

        public void accumulate(String sentiment) {
            if (sentiment == null) return;
            switch (sentiment.trim().toUpperCase()) {
                case "POSITIVE":
                    positive++;
                    break;
                case "NEUTRAL":
                    neutral++;
                    break;
                case "NEGATIVE":
                    negative++;
                    break;
                default:
                    // ignore invalid/empty values
                    break;
            }
        }

        public AspectSentimentSummary toSummary() {
            long total = positive + neutral + negative;
            int posPct = 0;
            int neuPct = 0;
            int negPct = 0;

            if (total > 0) {
                posPct = (int) Math.round((double) positive * 100 / total);
                neuPct = (int) Math.round((double) neutral * 100 / total);
                negPct = (int) Math.round((double) negative * 100 / total);

                // Rounding adjustment to make sure it sums up to 100% if we want,
                // but standard rounding is usually fine.
            }

            return AspectSentimentSummary.builder()
                    .positive(positive)
                    .neutral(neutral)
                    .negative(negative)
                    .positivePercentage(posPct)
                    .neutralPercentage(neuPct)
                    .negativePercentage(negPct)
                    .total(total)
                    .build();
        }
    }
}
