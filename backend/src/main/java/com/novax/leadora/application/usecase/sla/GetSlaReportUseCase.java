package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.response.SlaReportResponse;
import com.novax.leadora.api.dto.response.SlaReportResponse.ActivityBreakdown;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetSlaReportUseCase {

    private final SlaTrackingRepository slaTrackingRepository;

    private static final Map<String, String> ACTIVITY_LABELS = Map.of(
            "LEAD_RESPONSE",              "Lead Response",
            "QUOTATION_SENT",             "Quotation Dispatch",
            "FOLLOW_UP_TASK",             "Follow-up Task",
            "PAYMENT_DEPOSIT",            "Payment Deposit",
            "HANDOVER_SUBMISSION",        "Handover Submission",
            "QUOTATION_APPROVAL",         "Quotation Approval",
            "CUSTOMER_FEEDBACK_RESPONSE", "Customer Feedback Response"
    );

    /**
     * UC-17.6: Build SLA performance report with optional filters.
     *
     * @param from         report start date (inclusive)
     * @param to           report end date (inclusive)
     * @param activityType optional filter — null means all activity types
     * @param entityType   optional filter — null means all entity types
     */
    @Transactional(readOnly = true)
    public SlaReportResponse execute(LocalDate from, LocalDate to,
                                     String activityType, String entityType) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();

        OffsetDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toDt   = to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        List<SlaTrackingEntity> records = slaTrackingRepository
                .findForReport(fromDt, toDt, entityType, activityType);

        OffsetDateTime now = OffsetDateTime.now();

        // POST-3: log report access for audit
        log.info("SLA report viewed: from={}, to={}, activityType={}, entityType={}, totalRecords={}",
                from, to, activityType, entityType, records.size());

        // ── Classify each record ─────────────────────────────────────────────────
        int totalTracked  = records.size();
        int resolvedCount = 0, resolvedOnTimeCount = 0, breachedCount = 0, warningCount = 0, withinSlaCount = 0;
        double totalProcessingHours = 0;
        int resolvedWithTime = 0;

        // Group by activityType for breakdown
        Map<String, List<SlaTrackingEntity>> grouped = new LinkedHashMap<>();
        for (SlaTrackingEntity e : records) {
            grouped.computeIfAbsent(e.getActivityType(), k -> new ArrayList<>()).add(e);

            String ds = displayStatus(e, now);
            switch (ds) {
                case "RESOLVED"   -> {
                    resolvedCount++;
                    // Compliant only if resolved before the deadline
                    if (e.getResolvedAt() != null && !e.getResolvedAt().isAfter(e.getDeadlineAt())) {
                        resolvedOnTimeCount++;
                    }
                    if (e.getResolvedAt() != null) {
                        double hrs = Duration.between(e.getStartedAt(), e.getResolvedAt()).toMinutes() / 60.0;
                        if (hrs >= 0) { totalProcessingHours += hrs; resolvedWithTime++; }
                    }
                }
                case "BREACHED"   -> breachedCount++;
                case "WARNING"    -> warningCount++;
                default           -> withinSlaCount++;
            }
        }

        double breachRatePct     = totalTracked == 0 ? 0 : round2((double) breachedCount / totalTracked * 100);
        // Compliance = records that are still on-time (ACTIVE) + records resolved before breach
        double complianceRatePct = totalTracked == 0 ? 0
                : round2((double) (withinSlaCount + warningCount + resolvedOnTimeCount) / totalTracked * 100);
        double resolutionRatePct = (resolvedCount + breachedCount) == 0 ? 0
                : round2((double) resolvedCount / (resolvedCount + breachedCount) * 100);
        double avgProcessingHours = resolvedWithTime == 0 ? 0 : round2(totalProcessingHours / resolvedWithTime);

        // ── Build per-activity breakdown ─────────────────────────────────────────
        List<ActivityBreakdown> breakdown = new ArrayList<>();
        for (Map.Entry<String, List<SlaTrackingEntity>> entry : grouped.entrySet()) {
            String act  = entry.getKey();
            List<SlaTrackingEntity> group = entry.getValue();

            int gTotal = group.size(), gResolved = 0, gBreached = 0, gWarning = 0, gWithin = 0;
            double gTotalHrs = 0; int gResolvedCnt = 0;

            for (SlaTrackingEntity e : group) {
                String ds = displayStatus(e, now);
                switch (ds) {
                    case "RESOLVED" -> {
                        gResolved++;
                        if (e.getResolvedAt() != null) {
                            double h = Duration.between(e.getStartedAt(), e.getResolvedAt()).toMinutes() / 60.0;
                            if (h >= 0) { gTotalHrs += h; gResolvedCnt++; }
                        }
                    }
                    case "BREACHED" -> gBreached++;
                    case "WARNING"  -> gWarning++;
                    default         -> gWithin++;
                }
            }

            breakdown.add(ActivityBreakdown.builder()
                    .activityType(act)
                    .activityLabel(ACTIVITY_LABELS.getOrDefault(act, act))
                    .total(gTotal)
                    .resolved(gResolved)
                    .breached(gBreached)
                    .warning(gWarning)
                    .withinSla(gWithin)
                    .breachRatePct(gTotal == 0 ? 0 : round2((double) gBreached / gTotal * 100))
                    .avgProcessingHours(gResolvedCnt == 0 ? 0 : round2(gTotalHrs / gResolvedCnt))
                    .build());
        }

        return SlaReportResponse.builder()
                .fromDate(from.toString())
                .toDate(to.toString())
                .totalTracked(totalTracked)
                .resolvedCount(resolvedCount)
                .breachedCount(breachedCount)
                .warningCount(warningCount)
                .withinSlaCount(withinSlaCount)
                .breachRatePct(breachRatePct)
                .complianceRatePct(complianceRatePct)
                .resolutionRatePct(resolutionRatePct)
                .avgProcessingHours(avgProcessingHours)
                .byActivityType(breakdown)
                .build();
    }

    private static String displayStatus(SlaTrackingEntity e, OffsetDateTime now) {
        if (e.getStatus() == SlaStatus.RESOLVED) return "RESOLVED";
        if (e.getStatus() == SlaStatus.BREACHED || now.isAfter(e.getDeadlineAt())) return "BREACHED";
        if (now.isAfter(e.getWarningAt())) return "WARNING";
        return "WITHIN_SLA";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
