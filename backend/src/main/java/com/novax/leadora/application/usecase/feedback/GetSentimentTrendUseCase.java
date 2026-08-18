package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.SentimentTrendResponse;
import com.novax.leadora.api.dto.response.SentimentTrendResponse.AspectTrendSummary;
import com.novax.leadora.api.dto.response.SentimentTrendResponse.TrendPoint;
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
import java.time.temporal.WeekFields;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GetSentimentTrendUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public SentimentTrendResponse execute(
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            String groupBy,
            String headerUserId
    ) {
        UserEntity actor = currentUserProvider.resolve(headerUserId);
        String roleName = actor.getRole() != null ? actor.getRole().getRoleName().trim().toUpperCase() : "";

        Specification<SalesFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if ("SALES".equalsIgnoreCase(roleName) || "SALES_STAFF".equalsIgnoreCase(roleName)) {
                predicates.add(cb.equal(root.get("salesStaff").get("userId"), actor.getUserId()));
            }

            predicates.add(cb.isNotNull(root.get("submittedAt")));
            predicates.add(cb.equal(root.get("absaStatus"), "SUCCESS"));

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<SalesFeedbackEntity> feedbacks = salesFeedbackRepository.findAll(spec);

        // Grouping by period string using TreeMap to keep periods sorted chronologically
        Map<String, List<SalesFeedbackEntity>> grouped = new TreeMap<>();
        WeekFields weekFields = WeekFields.of(Locale.US);

        for (SalesFeedbackEntity f : feedbacks) {
            OffsetDateTime submittedAt = f.getSubmittedAt();
            String period;
            if ("month".equalsIgnoreCase(groupBy)) {
                period = String.format("%d-%02d", submittedAt.getYear(), submittedAt.getMonthValue());
            } else {
                // default to week
                int week = submittedAt.get(weekFields.weekOfWeekBasedYear());
                int year = submittedAt.get(weekFields.weekBasedYear());
                period = String.format("%d-W%02d", year, week);
            }
            grouped.computeIfAbsent(period, k -> new ArrayList<>()).add(f);
        }

        List<TrendPoint> points = new ArrayList<>();
        for (Map.Entry<String, List<SalesFeedbackEntity>> entry : grouped.entrySet()) {
            String period = entry.getKey();
            List<SalesFeedbackEntity> periodFeedbacks = entry.getValue();

            AspectTrendCounter overall = new AspectTrendCounter();
            AspectTrendCounter attitude = new AspectTrendCounter();
            AspectTrendCounter speed = new AspectTrendCounter();
            AspectTrendCounter accuracy = new AspectTrendCounter();
            AspectTrendCounter facility = new AspectTrendCounter();
            AspectTrendCounter price = new AspectTrendCounter();

            for (SalesFeedbackEntity f : periodFeedbacks) {
                // Overall satisfaction trend derived from rating (4-5 = Positive, 3 = Neutral, 1-2 = Negative)
                if (f.getRating() != null) {
                    int r = f.getRating();
                    if (r >= 4) {
                        overall.accumulate("POSITIVE");
                    } else if (r == 3) {
                        overall.accumulate("NEUTRAL");
                    } else {
                        overall.accumulate("NEGATIVE");
                    }
                }

                attitude.accumulate(f.getAbsaAttitudeSentiment());
                speed.accumulate(f.getAbsaSpeedSentiment());
                accuracy.accumulate(f.getAbsaAccuracySentiment());
                facility.accumulate(f.getAbsaFacilitySentiment());
                price.accumulate(f.getAbsaPriceSentiment());
            }

            points.add(TrendPoint.builder()
                    .period(period)
                    .overall(overall.toSummary())
                    .attitude(attitude.toSummary())
                    .speed(speed.toSummary())
                    .accuracy(accuracy.toSummary())
                    .facility(facility.toSummary())
                    .price(price.toSummary())
                    .build());
        }

        return SentimentTrendResponse.builder().points(points).build();
    }

    private static class AspectTrendCounter {
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
            }
        }

        public AspectTrendSummary toSummary() {
            return AspectTrendSummary.builder()
                    .positive(positive)
                    .neutral(neutral)
                    .negative(negative)
                    .build();
        }
    }
}
