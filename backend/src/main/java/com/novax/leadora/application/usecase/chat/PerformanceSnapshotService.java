package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.application.usecase.chat.time.ChatClock;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
import com.novax.leadora.application.usecase.reporting.GetSalesPerformanceReportUseCase;
import com.novax.leadora.application.usecase.reporting.GetTaskPerformanceReportUseCase;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * The staff-performance block of the reference data — conversion rates, win rates and per-person
 * results, the things a bare record count cannot express.
 *
 * <p><b>Why this reuses the reporting use cases instead of querying again.</b> Every figure here
 * already existed: {@link GetSalesPerformanceReportUseCase} and
 * {@link GetTaskPerformanceReportUseCase} back the Reporting screen, take the same date range, and
 * are already cached. Re-deriving them for chat would mean two definitions of "win rate" drifting
 * apart until the assistant and the screen contradicted each other in front of the same user. The
 * assistant reads the reports the company already agreed on; it does not invent a second set.
 *
 * <p><b>Scope (BR-36).</b> Sales performance is a whole-team report — it is offered only to a caller
 * {@link CrmSnapshotService#canSeeAllData} accepts, matching the {@code MANAGER/ADMIN} guard on the
 * reporting endpoint. Task performance is self-scoping: the use case narrows to the actor's own
 * tasks unless their role says otherwise, so it is safe for everyone.
 *
 * <p>Runs off the request thread like the rest of context gathering, so it must not receive a JPA
 * entity from the caller — it loads the acting user itself, inside the use case's own transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceSnapshotService {

    /** Enough to rank a sales team; past this a chat answer is the wrong medium anyway. */
    private static final int MAX_ROWS = 15;

    private final GetSalesPerformanceReportUseCase salesPerformance;
    private final GetTaskPerformanceReportUseCase taskPerformance;
    private final CrmSnapshotService crmSnapshotService;
    private final UserRepository userRepository;
    private final ChatClock clock;

    /**
     * Renders the performance block, or {@code ""} when nothing could be gathered.
     *
     * <p>Best-effort by the same rule as every other source: a failure here degrades the answer to
     * the ordinary CRM snapshot rather than failing the turn.
     */
    public String render(ChatActor actor, ChatDateRange range) {
        UserEntity user = userRepository.findWithRoleByUserId(actor.userId()).orElse(null);
        if (user == null) {
            return "";
        }

        LocalDate from = range.from();
        LocalDate to = range.to();
        StringBuilder sb = new StringBuilder("== Staff performance report ==\n");
        sb.append(period(range));

        boolean any = false;
        if (crmSnapshotService.canSeeAllData(actor)) {
            any |= appendSales(sb, from, to);
        } else {
            sb.append("This user's role may not read team-wide sales performance, so only their own "
                    + "task performance is shown. Do NOT name or compare other staff members.\n");
        }
        any |= appendTasks(sb, user, from, to);

        return any ? sb.toString() : "";
    }

    private boolean appendSales(StringBuilder sb, LocalDate from, LocalDate to) {
        SalesPerformanceReportResponse r;
        try {
            r = salesPerformance.execute(from, to);
        } catch (Exception ex) {
            log.warn("Sales performance unavailable for chat: {}", ex.getMessage());
            return false;
        }
        if (r == null) {
            return false;
        }

        sb.append("Funnel: leads created ").append(r.getLeadsCreated())
                .append(", qualified ").append(r.getQualifiedLeads())
                .append(", converted ").append(r.getLeadsConverted())
                .append(" (conversion rate ").append(r.getLeadConversionRate()).append("%)\n");
        sb.append("Deals: total ").append(r.getDealsTotal())
                .append(", open ").append(r.getDealsOpen())
                .append(", won ").append(r.getDealsWon())
                .append(", lost ").append(r.getDealsLost())
                .append(" (win rate ").append(r.getWinRate()).append("%)")
                .append(", won value ").append(r.getWonValue())
                .append(", open pipeline value ").append(r.getPipelineValue()).append("\n");
        sb.append("Quotations: created ").append(r.getQuotationsCreated())
                .append(", accepted ").append(r.getQuotationsAccepted())
                .append(" (acceptance rate ").append(r.getQuotationAcceptanceRate()).append("%)\n");
        sb.append("Bookings confirmed ").append(r.getBookingsConfirmed())
                .append(" (quotation-to-booking rate ").append(r.getQuotationToBookingRate())
                .append("%)\n");
        // The distinction the model must not blur: this is money actually received (PAID payments),
        // not the expected revenue carried on open deals.
        sb.append("REVENUE (sum of PAID payments — real money received, NOT expected deal value): ")
                .append(r.getRevenue()).append("\n");

        List<SalesPerformanceReportResponse.RepRow> reps = r.getReps();
        if (reps != null && !reps.isEmpty()) {
            sb.append("Per staff member (EXACT aggregates over every record in this period, ")
                    .append("ordered by revenue):\n");
            reps.stream().limit(MAX_ROWS).forEach(rep -> sb.append("  - ").append(rep.getName())
                    .append(": leads ").append(rep.getLeads())
                    .append(", deals won ").append(rep.getDealsWon())
                    .append(" worth ").append(rep.getWonValue())
                    .append(", bookings ").append(rep.getBookings())
                    .append(", revenue ").append(rep.getRevenue())
                    .append("\n"));
        }
        return true;
    }

    private boolean appendTasks(StringBuilder sb, UserEntity user, LocalDate from, LocalDate to) {
        TaskPerformanceReportResponse r;
        try {
            r = taskPerformance.execute(user, from, to);
        } catch (Exception ex) {
            log.warn("Task performance unavailable for chat: {}", ex.getMessage());
            return false;
        }
        if (r == null) {
            return false;
        }

        sb.append("Tasks: total ").append(r.getTotalTasks())
                .append(", completed ").append(r.getCompleted())
                .append(", open ").append(r.getOpen())
                .append(", cancelled ").append(r.getCancelled())
                .append(", overdue ").append(r.getOverdue())
                .append(" (completion rate ").append(r.getCompletionRate())
                .append("%, overdue rate ").append(r.getOverdueRate()).append("%)\n");

        List<TaskPerformanceReportResponse.StaffRow> staff = r.getStaff();
        if (staff != null && !staff.isEmpty()) {
            sb.append("Task completion per staff member (EXACT, all their tasks in this period):\n");
            staff.stream().limit(MAX_ROWS).forEach(s -> sb.append("  - ").append(s.getName())
                    .append(": total ").append(s.getTotal())
                    .append(", completed ").append(s.getCompleted())
                    .append(", overdue ").append(s.getOverdue())
                    .append(" (completion rate ").append(s.getCompletionRate()).append("%)\n"));
        }
        return true;
    }

    /**
     * Names the window, and warns about the one place chat and the Reporting screen can disagree.
     *
     * <p>The reporting use cases convert a {@code LocalDate} to instants at <b>UTC</b>
     * ({@code ReportingUtils}), while the rest of the chat snapshot uses the business timezone. For
     * every period longer than a day the difference is immaterial; for "today" it shifts the window
     * by the UTC offset. Rather than fork the shared reporting logic — which would change the
     * numbers on the Reporting screen too — the discrepancy is declared here so the assistant can
     * hedge a single-day performance figure instead of overstating it.
     */
    private String period(ChatDateRange range) {
        if (range.isAllTime()) {
            return "Period: ALL TIME (no date filter).\n";
        }
        String line = "Period: " + range.label() + " (" + range.from() + " .. " + range.to()
                + "). Every figure below covers ONLY this period.\n";
        if (range.from().equals(range.to())) {
            line += "NOTE: this report counts a day in UTC, while the CRM figures elsewhere in this "
                    + "reference data use " + clock.zone() + ". For a single-day period the two can "
                    + "differ slightly at the edges — say the figure is approximate for one day.\n";
        }
        return line;
    }
}
