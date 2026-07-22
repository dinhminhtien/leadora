package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.application.usecase.chat.dto.ChatCounts;
import com.novax.leadora.application.usecase.chat.dto.RepDealStat;
import com.novax.leadora.application.usecase.chat.dto.RepLeadCount;
import com.novax.leadora.application.usecase.chat.dto.StatusBucket;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.ChatAggregateRepository;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Step [2] of the hybrid pipeline: read-only retrieval of CRM facts, scope-enforced in code.
 *
 * <p>Data scope (BR-36) is applied with {@code WHERE assigned_user_id = ...} in every query — the
 * assistant can never receive rows outside the requested scope, independent of what the LLM does
 * with the text. Output is a compact, English, human-readable block stuffed into the prompt.
 *
 * <p><b>Aggregate in the database, list only what is shown.</b> Counts and sums come from
 * {@code GROUP BY} queries and listings are capped with {@code Pageable}, so the work is O(rows
 * displayed) rather than O(table).
 *
 * <p><b>Detail is proportionate to the question.</b> Every area contributes its counts, because
 * one line each is cheap and lets the assistant answer "how many bookings?" whatever was asked.
 * Row-by-row listings are only produced for the areas the question actually mentions: listing all
 * seven areas at once runs to thousands of tokens on every turn, which costs money, slows the
 * model's prefill, and buries the relevant rows among irrelevant ones.
 *
 * <p>Every method returns plain strings, holding no managed entities, so callers may run them off
 * the request thread and outside the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class CrmSnapshotService {

    // Listing caps. Deliberately small: chat is not a data grid, and every row costs tokens on
    // every turn. Since each listing header carries a link to the screen holding the full list,
    // a bigger cap buys a longer answer nobody reads rather than a more useful one. Ten rows is
    // about as much as a chat bubble can show before it stops being scannable.
    private static final int MAX_LEADS = 10;
    private static final int MAX_DEALS = 10;
    private static final int MAX_TASKS = 10;
    private static final int MAX_QUOTATIONS = 10;
    private static final int MAX_BOOKINGS = 10;
    private static final int MAX_PAYMENTS = 8;
    private static final int MAX_CUSTOMERS = 10;
    private static final int MAX_REPS = 20;

    /** How many staff members to name when suggesting whose records to ask about instead. */
    private static final int MAX_SUGGESTED_REPS = 6;

    /** A task is never "overdue" once it is closed — BR-17 derives the flag, it is not stored. */
    private static final List<TaskStatus> CLOSED_TASK_STATUSES =
            List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);

    /** Every area's counts in one round trip; the per-entity repositories only fetch listings. */
    private final ChatAggregateRepository chatAggregateRepository;
    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;
    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;

    /**
     * Roles allowed to see ALL CRM records via chat. Any other role is scoped to their own assigned
     * records. Optionally widened by {@code AI_CHAT_TOP_PRIVILEGE} (dev escape hatch).
     */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");

    @Value("${AI_CHAT_TOP_PRIVILEGE:false}")
    private boolean topPrivilege;

    /** Whether {@code user}'s role may read every record (team-wide), vs only their own. */
    public boolean canSeeAllData(ChatActor actor) {
        if (topPrivilege) {
            return true;
        }
        String role = actor.roleName() != null ? actor.roleName().trim().toUpperCase() : "";
        return FULL_SCOPE_ROLES.contains(role);
    }

    /**
     * Facts the user may view: team-wide for Manager/Admin, own otherwise. Use for generic CRM
     * questions ("what leads are there?").
     */
    public String scopedSnapshot(ChatActor actor, Set<CrmArea> areas) {
        boolean all = canSeeAllData(actor);
        return snapshot(actor, all ? null : actor.userId(), areas,
                all ? "== Full CRM data (manager access) =="
                        : "== CRM data assigned to " + actor.fullName() + " ==");
    }

    /**
     * Facts about the records assigned to this user personally, <b>whatever their role</b>.
     *
     * <p>Use when the question carries an explicit possessive ("lead <em>của tôi</em>", "<em>my</em>
     * deals"). A Manager asking for "my leads" means the ones assigned to them, not the whole
     * company's — answering with everything silently ignores the word they emphasised.
     */
    public String personalSnapshot(ChatActor actor, Set<CrmArea> areas) {
        return snapshot(actor, actor.userId(), areas,
                "== CRM data assigned personally to " + actor.fullName() + " ==");
    }

    private String snapshot(ChatActor actor, UUID scopeUserId, Set<CrmArea> areas, String header) {
        OffsetDateTime now = OffsetDateTime.now();
        // Every area's counts in one round trip. Fetching them area by area cost more in network
        // latency to a remote database than the model spent producing its first token.
        ChatCounts counts = chatAggregateRepository.countAll(scopeUserId);
        StringBuilder sb = new StringBuilder(header).append("\n");
        List<CrmArea> emptyAreas = new ArrayList<>();

        for (CrmArea area : CrmArea.values()) {
            boolean detail = areas.contains(area);
            long total = switch (area) {
                case LEADS -> appendLeads(sb, counts, scopeUserId, detail);
                case DEALS -> appendDeals(sb, counts, scopeUserId, detail);
                case TASKS -> appendTasks(sb, counts, scopeUserId, detail, now);
                case QUOTATIONS -> appendQuotations(sb, counts, scopeUserId, detail);
                case BOOKINGS -> appendBookings(sb, counts, scopeUserId, detail);
                case PAYMENTS -> appendPayments(sb, counts, scopeUserId, detail);
                case CUSTOMERS -> appendCustomers(sb, counts, scopeUserId, detail);
            };
            // Guidance is only worth giving for what was actually asked about: an empty payments
            // area is not interesting when the question was about leads.
            if (total == 0 && detail) {
                emptyAreas.add(area);
            }
        }

        if (!emptyAreas.isEmpty()) {
            appendAffordances(sb, actor, scopeUserId, emptyAreas);
        }
        return sb.toString();
    }

    // ── Per-area sections ─────────────────────────────────────────────────────
    // Each returns the area's row count so the caller can spot empty areas.

    private long appendLeads(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail) {
        long total = counts.total(CrmArea.LEADS);
        sb.append("Leads: total ").append(total)
                .append(statusBreakdown(counts.of(CrmArea.LEADS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Lead list, newest first", Math.min(total, MAX_LEADS), total, CrmArea.LEADS));
            leadRepository.findRecentForChat(scope, page(MAX_LEADS)).forEach(l ->
                    sb.append("  - \"").append(l.getFullName())
                            .append("\" | ").append(l.getStatus())
                            .append(" | company: ").append(dash(l.getCompanyName()))
                            .append(" | email: ").append(dash(l.getEmail()))
                            .append(" | source: ").append(dash(l.getSource()))
                            .append(" | assigned to: ").append(assigneeLabel(l.getAssignedUser()))
                            .append(" | created: ").append(l.getCreatedAt()).append("\n"));
        }
        return total;
    }

    private long appendDeals(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail) {
        long total = counts.total(CrmArea.DEALS);
        sb.append("Deals: total ").append(total)
                .append(", open ").append(counts.count(CrmArea.DEALS, DealStatus.OPEN.name()))
                .append(", expected value (OPEN) ")
                .append(counts.amount(CrmArea.DEALS, DealStatus.OPEN.name()))
                .append(", won value (WON) ")
                .append(counts.amount(CrmArea.DEALS, DealStatus.WON.name())).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Deal details, newest first", Math.min(total, MAX_DEALS), total, CrmArea.DEALS));
            dealRepository.findRecentForChat(scope, page(MAX_DEALS)).forEach(d ->
                    sb.append("  - \"").append(d.getDealName())
                            .append("\" | ").append(d.getPipelineStage())
                            .append(" | ").append(d.getStatus())
                            .append(" | value ").append(d.getExpectedRevenue())
                            .append(" | expected close ").append(d.getExpectedCloseDate())
                            .append(" | assigned to: ").append(assigneeLabel(d.getAssignedUser()))
                            .append("\n"));
        }
        return total;
    }

    private long appendTasks(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                             OffsetDateTime now) {
        long total = counts.total(CrmArea.TASKS);
        long open = counts.count(CrmArea.TASKS, TaskStatus.OPEN.name());
        sb.append("Tasks: total ").append(total)
                .append(", open/in progress ").append(open)
                .append(", overdue ").append(counts.overdueTasks())
                .append("\n");

        if (detail && open > 0) {
            sb.append(listingHeader("Open tasks, earliest deadline first", Math.min(open, MAX_TASKS), open, CrmArea.TASKS));
            taskRepository.findOpenForChat(scope, CLOSED_TASK_STATUSES, page(MAX_TASKS)).forEach(t ->
                    sb.append("  - \"").append(t.getTitle())
                            .append("\" | due ").append(t.getEndAt())
                            .append(" | priority ").append(t.getPriority())
                            .append(" | ").append(t.getStatus())
                            .append(isOverdue(t, now) ? " | OVERDUE" : "").append("\n"));
        }
        return total;
    }

    private long appendQuotations(StringBuilder sb, ChatCounts counts, UUID scope,
                                  boolean detail) {
        long total = counts.total(CrmArea.QUOTATIONS);
        sb.append("Quotations: total ").append(total)
                .append(valueBreakdown(counts.of(CrmArea.QUOTATIONS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Quotation details, newest first", Math.min(total, MAX_QUOTATIONS), total, CrmArea.QUOTATIONS));
            quotationRepository.findRecentForChat(scope, page(MAX_QUOTATIONS)).forEach(q ->
                    sb.append("  - v").append(q.getVersion())
                            .append(" | ").append(q.getStatus())
                            .append(" | customer: ")
                            .append(q.getCustomer() != null ? q.getCustomer().getFullName() : "-")
                            .append(" | deal: ")
                            .append(q.getDeal() != null ? q.getDeal().getDealName() : "-")
                            .append(" | total ").append(q.getTotalAmount())
                            .append(" | room: ").append(dash(q.getRoomType()))
                            .append(" | stay ").append(q.getCheckInDate())
                            .append(" -> ").append(q.getCheckOutDate())
                            .append(" | valid until ").append(q.getValidUntil()).append("\n"));
        }
        return total;
    }

    private long appendBookings(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail) {
        long total = counts.total(CrmArea.BOOKINGS);
        sb.append("Bookings: total ").append(total)
                .append(valueBreakdown(counts.of(CrmArea.BOOKINGS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Booking details, newest first", Math.min(total, MAX_BOOKINGS), total, CrmArea.BOOKINGS));
            bookingRepository.findRecentForChat(scope, page(MAX_BOOKINGS)).forEach(b ->
                    sb.append("  - \"").append(b.getBookingCode())
                            .append("\" | ").append(b.getStatus())
                            .append(" | customer: ")
                            .append(b.getCustomer() != null ? b.getCustomer().getFullName() : "-")
                            .append(" | stay ").append(b.getCheckInDate())
                            .append(" -> ").append(b.getCheckOutDate())
                            .append(" | total ").append(b.getTotalAmount())
                            .append(" | assigned to: ").append(assigneeLabel(b.getAssignedUser()))
                            .append("\n"));
        }
        return total;
    }

    private long appendPayments(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail) {
        long total = counts.total(CrmArea.PAYMENTS);
        sb.append("Payments: total ").append(total)
                .append(", PAID amount ").append(counts.amount(CrmArea.PAYMENTS, "PAID"))
                .append(", PENDING amount ").append(counts.amount(CrmArea.PAYMENTS, "PENDING"))
                .append(statusBreakdown(counts.of(CrmArea.PAYMENTS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Payment details, newest first", Math.min(total, MAX_PAYMENTS), total, CrmArea.PAYMENTS));
            paymentRepository.findRecentForChat(scope, page(MAX_PAYMENTS)).forEach(p ->
                    sb.append("  - booking ")
                            .append(p.getBooking() != null ? p.getBooking().getBookingCode() : "-")
                            .append(" | ").append(p.getPaymentType())
                            .append(" | ").append(p.getStatus())
                            .append(" | amount ").append(p.getAmount())
                            .append(" | due ").append(p.getDueDate())
                            .append(" | paid at ").append(p.getPaidAt()).append("\n"));
        }
        return total;
    }

    private long appendCustomers(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail) {
        long total = counts.total(CrmArea.CUSTOMERS);
        sb.append("Customers: total ").append(total)
                .append(statusBreakdown(counts.of(CrmArea.CUSTOMERS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Customer list, newest first", Math.min(total, MAX_CUSTOMERS), total, CrmArea.CUSTOMERS));
            customerRepository.findRecentForChat(scope, page(MAX_CUSTOMERS)).forEach(c ->
                    sb.append("  - \"").append(c.getFullName())
                            .append("\" | ").append(c.getStatus())
                            .append(" | ").append(c.getCustomerType())
                            .append(" | company: ").append(dash(c.getCompanyName()))
                            .append(" | email: ").append(dash(c.getEmail()))
                            .append(" | phone: ").append(dash(c.getPhone()))
                            .append(" | assigned to: ").append(assigneeLabel(c.getAssignedUser()))
                            .append("\n"));
        }
        return total;
    }

    /**
     * Explains an empty result and supplies the facts the assistant may turn into follow-up
     * suggestions (system prompt rule 3c). Without this the model can only report "no data", or —
     * worse — invent plausible-looking colleagues to suggest.
     *
     * <p><b>BR-36:</b> the per-staff breakdown is only ever added for a caller allowed to see all
     * records. For everyone else the block explicitly forbids naming colleagues, because merely
     * listing who holds the records would leak data the caller cannot read.
     */
    private void appendAffordances(StringBuilder sb, ChatActor actor, UUID scopeUserId,
                                   List<CrmArea> emptyAreas) {
        boolean personalScope = scopeUserId != null;
        boolean privileged = canSeeAllData(actor);

        StringJoiner names = new StringJoiner(", ");
        emptyAreas.forEach(a -> names.add(a.name().toLowerCase()));

        sb.append("\n-- WHY SOME AREAS ARE EMPTY, AND WHAT YOU MAY OFFER --\n")
                .append("These facts are real. Build follow-up suggestions ONLY from them, and ")
                .append("never mention a name or figure that does not appear here.\n")
                .append("Empty for this scope: ").append(names).append(".\n");

        if (personalScope) {
            sb.append("Nothing in those areas is assigned to ").append(actor.fullName());
            sb.append(privileged
                    ? ", whose role can nevertheless view every record — the personal scope is "
                      + "empty there, the company's data is not.\n"
                    : ".\n");
        } else {
            sb.append("No records exist there in the scope this user is allowed to read.\n");
        }

        if (!privileged) {
            // BR-36: naming who does hold the records would itself disclose data this caller
            // cannot read, so the suggestions must stay inside their own scope.
            sb.append("This user may only read their own records, so do NOT name other staff ")
                    .append("members and do NOT offer team-wide figures. You may suggest asking ")
                    .append("about the areas above that are NOT empty, about company documents ")
                    .append("and policies, or contacting their manager about record assignment.\n");
            return;
        }

        if (!personalScope) {
            // The snapshot already covers every record, so there is no wider scope to fall back
            // on: these areas are empty company-wide, and re-querying would just repeat zeros.
            sb.append("This scope already covers every record, so those areas are empty ")
                    .append("company-wide — there is no wider scope to offer. You may suggest ")
                    .append("the areas above that are NOT empty, or company documents.\n");
            return;
        }

        // One more round trip covers the fallback for every empty area, however many there are.
        ChatCounts companyWide = chatAggregateRepository.countAll(null);
        for (CrmArea area : emptyAreas) {
            appendCompanyWideFallback(sb, area, companyWide);
        }
    }

    /** Company-wide figures a privileged caller can be offered when their own scope is empty. */
    private void appendCompanyWideFallback(StringBuilder sb, CrmArea area, ChatCounts companyWide) {
        sb.append("Company-wide ").append(area.name().toLowerCase()).append(": ")
                .append(companyWide.total(area));

        switch (area) {
            case LEADS -> {
                sb.append(statusBreakdown(companyWide.of(CrmArea.LEADS))).append("\n");
                List<RepLeadCount> perRep = leadRepository.countPerAssignee(page(MAX_SUGGESTED_REPS));
                if (!perRep.isEmpty()) {
                    StringJoiner reps = new StringJoiner(", ");
                    perRep.forEach(r -> reps.add(r.repName() + "=" + r.count()));
                    sb.append("Leads per staff member: ").append(reps).append("\n")
                            .append("You may offer the leads of any staff member named above, a ")
                            .append("team-wide summary, or a specific lead status.\n");
                }
            }
            case DEALS -> sb.append(valueBreakdown(companyWide.of(CrmArea.DEALS)))
                    .append("\nYou may offer a team-wide deal summary or a named staff ")
                    .append("member's deals.\n");
            case TASKS -> sb.append(", of which overdue: ").append(companyWide.overdueTasks())
                    .append("\nYou may offer the team's overdue tasks.\n");
            case QUOTATIONS -> sb.append("\nYou may offer the team's quotations.\n");
            case BOOKINGS -> sb.append("\nYou may offer the team's bookings.\n");
            case PAYMENTS -> sb.append("\nYou may offer the team's payments.\n");
            case CUSTOMERS -> sb.append("\nYou may offer the team's customers.\n");
        }
    }

    /** Team-wide aggregates across all sales reps. Caller must check {@link #canSeeAllData}. */
    public String teamSummary() {
        // Two round trips for the whole summary: every area's counts, then the per-rep pivot.
        ChatCounts counts = chatAggregateRepository.countAll(null);
        StringBuilder sb = new StringBuilder("== Whole sales team summary ==\n");

        sb.append("Deals: total ").append(counts.total(CrmArea.DEALS))
                .append(" (open ").append(counts.count(CrmArea.DEALS, DealStatus.OPEN.name()))
                .append(", won ").append(counts.count(CrmArea.DEALS, DealStatus.WON.name()))
                .append(", lost ").append(counts.count(CrmArea.DEALS, DealStatus.LOST.name()))
                .append(")")
                .append(", WON value ").append(counts.amount(CrmArea.DEALS, DealStatus.WON.name()))
                .append(", pipeline value (OPEN) ")
                .append(counts.amount(CrmArea.DEALS, DealStatus.OPEN.name()))
                .append("\n");

        sb.append("Leads: total ").append(counts.total(CrmArea.LEADS)).append("\n");
        sb.append("Tasks: total ").append(counts.total(CrmArea.TASKS))
                .append(", overdue ").append(counts.overdueTasks()).append("\n");
        sb.append("Quotations: total ").append(counts.total(CrmArea.QUOTATIONS)).append("\n");
        sb.append("Bookings: total ").append(counts.total(CrmArea.BOOKINGS)).append("\n");
        sb.append("Customers: total ").append(counts.total(CrmArea.CUSTOMERS)).append("\n");

        // Per-rep breakdown: the query returns one row per (rep, status); pivot to one line per rep.
        List<RepDealStat> stats = dealRepository.statsPerAssignee();
        List<String> reps = stats.stream().map(RepDealStat::repName).distinct().limit(MAX_REPS).toList();
        if (!reps.isEmpty()) {
            sb.append("By staff member (up to ").append(MAX_REPS).append("):\n");
            for (String rep : reps) {
                List<RepDealStat> forRep = stats.stream().filter(s -> rep.equals(s.repName())).toList();
                sb.append("  - ").append(rep)
                        .append(": open deals ").append(repCount(forRep, DealStatus.OPEN))
                        .append(", won ").append(repCount(forRep, DealStatus.WON))
                        .append(", WON value ").append(repValue(forRep, DealStatus.WON))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Header for a listing, stating how much of the area it actually covers and where the rest is.
     *
     * <p>A capped list read as a complete one is a quiet way to be wrong: "here are your leads"
     * over 25 of 143 rows is a false answer to "show me all my leads". Naming both numbers lets
     * the assistant say which it is, and carrying the screen path means it can hand the full list
     * to the UI instead of trying to paginate through chat.
     */
    private static String listingHeader(String noun, long shown, long total, CrmArea area) {
        StringBuilder h = new StringBuilder(noun)
                .append(" (showing ").append(shown).append(" of ").append(total);
        if (total > shown) {
            h.append("; TRUNCATED - the remaining ").append(total - shown)
                    .append(" are only on the screen below");
        }
        h.append(") | full list: ").append(area.screenLabel())
                .append(" screen at ").append(area.screenPath()).append("\n");
        return h.toString();
    }

    private static Pageable page(int size) {
        return PageRequest.of(0, size);
    }

    /** Renders " {NEW=8, LOST=5}", or "" when the area has no records. */
    private static String statusBreakdown(List<StatusBucket> buckets) {
        return render(buckets, b -> b.status() + "=" + b.count());
    }

    /** As above, with each status's total value — for the areas that carry money. */
    private static String valueBreakdown(List<StatusBucket> buckets) {
        return render(buckets, b -> b.status() + "=" + b.count() + " worth " + b.amountOrZero());
    }

    private static String render(List<StatusBucket> buckets,
                                 java.util.function.Function<StatusBucket, String> format) {
        if (buckets.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ", " {", "}");
        buckets.forEach(b -> joiner.add(format.apply(b)));
        return joiner.toString();
    }

    private static long repCount(List<RepDealStat> stats, DealStatus status) {
        return stats.stream().filter(s -> s.status() == status)
                .mapToLong(RepDealStat::count).sum();
    }

    private static BigDecimal repValue(List<RepDealStat> stats, DealStatus status) {
        return stats.stream().filter(s -> s.status() == status)
                .map(RepDealStat::revenueOrZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String dash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    /** BR-17: overdue is derived, never stored — not closed, and past its {@code end_at}. */
    private static boolean isOverdue(TaskEntity task, OffsetDateTime now) {
        return task.getEndAt() != null
                && task.getEndAt().isBefore(now)
                && !CLOSED_TASK_STATUSES.contains(task.getStatus());
    }

    private static String assigneeLabel(UserEntity assignee) {
        if (assignee == null) {
            return "(unassigned)";
        }
        return assignee.getFullName() != null ? assignee.getFullName() : assignee.getUserId().toString();
    }
}
