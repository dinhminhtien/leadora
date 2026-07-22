package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.application.usecase.chat.dto.DealStatusAggregate;
import com.novax.leadora.application.usecase.chat.dto.LeadStatusCount;
import com.novax.leadora.application.usecase.chat.dto.RepDealStat;
import com.novax.leadora.application.usecase.chat.dto.RepLeadCount;
import com.novax.leadora.application.usecase.chat.dto.TaskStatusCount;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
 * displayed) rather than O(table). The previous implementation loaded whole tables via
 * {@code findAll()} and counted in the JVM, which also triggered one extra query per row when
 * rendering the assignee (the association is lazy and {@code findAll()} carries no entity graph).
 *
 * <p>Every method returns plain strings, holding no managed entities, so callers may run them off
 * the request thread and outside the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class CrmSnapshotService {

    private static final int MAX_LEADS = 25;
    private static final int MAX_DEALS = 15;
    private static final int MAX_TASKS = 10;
    private static final int MAX_REPS = 20;

    /** How many staff members to name when suggesting whose records to ask about instead. */
    private static final int MAX_SUGGESTED_REPS = 6;

    /** A task is never "overdue" once it is closed — BR-17 derives the flag, it is not stored. */
    private static final List<TaskStatus> CLOSED_TASK_STATUSES =
            List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);

    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;

    /**
     * Roles allowed to see ALL CRM records via chat. Any other role is scoped to their own assigned
     * records — so "show me the leads" returns just their leads, while a Manager/Admin sees the
     * whole team's. Optionally widened by {@code AI_CHAT_TOP_PRIVILEGE} (dev escape hatch).
     */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");

    @Value("${AI_CHAT_TOP_PRIVILEGE:false}")
    private boolean topPrivilege;

    /** Whether {@code user}'s role may read every record (team-wide), vs only their own. */
    public boolean canSeeAllData(UserEntity user) {
        if (topPrivilege) {
            return true;
        }
        String role = (user.getRole() != null && user.getRole().getRoleName() != null)
                ? user.getRole().getRoleName().trim().toUpperCase() : "";
        return FULL_SCOPE_ROLES.contains(role);
    }

    /**
     * Facts about leads/deals/tasks the user may view: team-wide for Manager/Admin, own otherwise.
     * Use for generic CRM questions ("what leads are there?").
     */
    public String scopedSnapshot(UserEntity user) {
        boolean all = canSeeAllData(user);
        return snapshot(user, all ? null : user.getUserId(),
                all ? "== Full CRM data (manager access) =="
                        : "== CRM data assigned to " + user.getFullName() + " ==");
    }

    /**
     * Facts about the records assigned to this user personally, <b>whatever their role</b>.
     *
     * <p>Use when the question carries an explicit possessive ("lead <em>của tôi</em>", "<em>my</em>
     * deals"). A Manager asking for "my leads" means the ones assigned to them, not the whole
     * company's — answering with everything silently ignores the word they emphasised. When that
     * personal scope turns out to be empty, the snapshot carries the facts needed to suggest a
     * useful next question instead of dead-ending.
     */
    public String personalSnapshot(UserEntity user) {
        return snapshot(user, user.getUserId(),
                "== CRM data assigned personally to " + user.getFullName() + " ==");
    }

    private String snapshot(UserEntity user, UUID scopeUserId, String header) {
        OffsetDateTime now = OffsetDateTime.now();
        StringBuilder sb = new StringBuilder(header).append("\n");

        // ── Leads: counts from the database, listing capped at what we actually print ──
        List<LeadStatusCount> leadCounts = leadRepository.countByStatusForChat(scopeUserId);
        long leadTotal = leadCounts.stream().mapToLong(LeadStatusCount::count).sum();
        sb.append("Leads: total ").append(leadTotal);
        if (!leadCounts.isEmpty()) {
            StringJoiner byStatus = new StringJoiner(", ", " {", "}");
            leadCounts.forEach(c -> byStatus.add(c.status() + "=" + c.count()));
            sb.append(byStatus);
        }
        sb.append("\n");

        if (leadTotal > 0) {
            List<LeadEntity> leads =
                    leadRepository.findRecentForChat(scopeUserId, PageRequest.of(0, MAX_LEADS));
            sb.append("Lead list (newest first, up to ").append(MAX_LEADS).append("):\n");
            leads.forEach(l -> sb.append("  - \"").append(l.getFullName())
                    .append("\" | ").append(l.getStatus())
                    .append(" | company: ").append(nullToDash(l.getCompanyName()))
                    .append(" | email: ").append(nullToDash(l.getEmail()))
                    .append(" | source: ").append(nullToDash(l.getSource()))
                    .append(" | assigned to: ").append(assigneeLabel(l.getAssignedUser()))
                    .append(" | created: ").append(l.getCreatedAt())
                    .append("\n"));
        }

        // ── Deals ──
        List<DealStatusAggregate> dealAgg = dealRepository.aggregateByStatusForChat(scopeUserId);
        long dealTotal = dealAgg.stream().mapToLong(DealStatusAggregate::count).sum();
        sb.append("Deals: total ").append(dealTotal)
                .append(", open ").append(countOf(dealAgg, DealStatus.OPEN))
                .append(", expected value (OPEN) ").append(valueOf(dealAgg, DealStatus.OPEN))
                .append(", won value (WON) ").append(valueOf(dealAgg, DealStatus.WON)).append("\n");

        if (dealTotal > 0) {
            List<DealEntity> deals =
                    dealRepository.findRecentForChat(scopeUserId, PageRequest.of(0, MAX_DEALS));
            sb.append("Deal details (up to ").append(MAX_DEALS).append("):\n");
            deals.forEach(d -> sb.append("  - \"").append(d.getDealName())
                    .append("\" | ").append(d.getPipelineStage())
                    .append(" | ").append(d.getStatus())
                    .append(" | value ").append(d.getExpectedRevenue())
                    .append(" | expected close ").append(d.getExpectedCloseDate())
                    .append(" | assigned to: ").append(assigneeLabel(d.getAssignedUser()))
                    .append("\n"));
        }

        // ── Tasks (overdue is derived: not closed, and past end_at) ──
        List<TaskStatusCount> taskCounts = taskRepository.countByStatusForChat(scopeUserId);
        long taskTotal = taskCounts.stream().mapToLong(TaskStatusCount::count).sum();
        long openTasks = taskCounts.stream()
                .filter(c -> c.status() == TaskStatus.OPEN)
                .mapToLong(TaskStatusCount::count).sum();
        long overdueCount =
                taskRepository.countOverdueForChat(scopeUserId, CLOSED_TASK_STATUSES, now);
        sb.append("Tasks: total ").append(taskTotal)
                .append(", open/in progress ").append(openTasks)
                .append(", overdue ").append(overdueCount).append("\n");

        if (openTasks > 0) {
            List<TaskEntity> open = taskRepository.findOpenForChat(
                    scopeUserId, CLOSED_TASK_STATUSES, PageRequest.of(0, MAX_TASKS));
            sb.append("Open tasks (up to ").append(MAX_TASKS)
                    .append(", earliest deadline first):\n");
            open.forEach(t -> sb.append("  - \"").append(t.getTitle())
                    .append("\" | due ").append(t.getEndAt())
                    .append(" | priority ").append(t.getPriority())
                    .append(" | ").append(t.getStatus())
                    .append(isOverdue(t, now) ? " | OVERDUE" : "")
                    .append("\n"));
        }

        // Guidance is emitted per area, not only when the whole snapshot is empty. Someone with
        // no leads but a couple of deals still needs an answer to "show my leads" that goes
        // further than "none" — and that combination is the common case, not the rare one.
        List<String> emptyAreas = new ArrayList<>();
        if (leadTotal == 0) {
            emptyAreas.add("leads");
        }
        if (dealTotal == 0) {
            emptyAreas.add("deals");
        }
        if (taskTotal == 0) {
            emptyAreas.add("tasks");
        }
        if (!emptyAreas.isEmpty()) {
            appendAffordances(sb, user, scopeUserId, emptyAreas);
        }
        return sb.toString();
    }

    /**
     * Explains an empty result and supplies the facts the assistant may turn into follow-up
     * suggestions (system prompt rule 3c). Without this the model can only report "no data", or —
     * worse — invent plausible-looking colleagues to suggest.
     *
     * <p><b>BR-36:</b> the per-staff breakdown is only ever added for a caller allowed to see all
     * records. For everyone else the block explicitly forbids naming colleagues, because merely
     * listing who holds the leads would leak data the caller cannot read.
     */
    private void appendAffordances(StringBuilder sb, UserEntity user, UUID scopeUserId,
                                   List<String> emptyAreas) {
        boolean personalScope = scopeUserId != null;
        boolean privileged = canSeeAllData(user);

        sb.append("\n-- WHY SOME AREAS ARE EMPTY, AND WHAT YOU MAY OFFER --\n");
        sb.append("These facts are real. Build follow-up suggestions ONLY from them, and never ")
                .append("mention a name or figure that does not appear here.\n");
        sb.append("Empty for this scope: ").append(String.join(", ", emptyAreas)).append(".\n");

        if (personalScope && privileged) {
            sb.append("Nothing in those areas is assigned directly to ").append(user.getFullName())
                    .append(", whose role can nevertheless view every record. ")
                    .append("The personal scope is empty there; the company's data is not.\n");
        } else if (personalScope) {
            sb.append("Nothing in those areas is currently assigned to ")
                    .append(user.getFullName()).append(".\n");
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
            // on: these areas are empty company-wide. Re-querying would repeat the counts printed
            // above and add a meaningless "company-wide: 0" line.
            sb.append("This scope already covers every record, so those areas are empty ")
                    .append("company-wide — there is no wider scope to offer. You may suggest ")
                    .append("the areas above that are NOT empty, or company documents.\n");
            return;
        }

        if (emptyAreas.contains("leads")) {
            List<LeadStatusCount> byStatus = leadRepository.countByStatusForChat(null);
            long companyLeads = byStatus.stream().mapToLong(LeadStatusCount::count).sum();
            sb.append("Company-wide leads: ").append(companyLeads);
            if (!byStatus.isEmpty()) {
                StringJoiner statuses = new StringJoiner(", ", " (", ")");
                byStatus.forEach(c -> statuses.add(c.status() + "=" + c.count()));
                sb.append(statuses);
            }
            sb.append("\n");

            List<RepLeadCount> perRep =
                    leadRepository.countPerAssignee(PageRequest.of(0, MAX_SUGGESTED_REPS));
            if (!perRep.isEmpty()) {
                StringJoiner reps = new StringJoiner(", ");
                perRep.forEach(r -> reps.add(r.repName() + "=" + r.count()));
                sb.append("Leads per staff member: ").append(reps).append("\n");
                sb.append("You may offer the leads of any staff member named above, a team-wide ")
                        .append("summary, or a specific lead status.\n");
            }
        }

        if (emptyAreas.contains("deals")) {
            List<DealStatusAggregate> agg = dealRepository.aggregateByStatusForChat(null);
            long companyDeals = agg.stream().mapToLong(DealStatusAggregate::count).sum();
            sb.append("Company-wide deals: ").append(companyDeals);
            if (!agg.isEmpty()) {
                StringJoiner statuses = new StringJoiner(", ", " (", ")");
                agg.forEach(a -> statuses.add(a.status() + "=" + a.count()
                        + " worth " + a.revenueOrZero()));
                sb.append(statuses);
            }
            sb.append("\nYou may offer a team-wide deal summary or the deals of a named staff member.\n");
        }

        if (emptyAreas.contains("tasks")) {
            long companyTasks = taskRepository.countByStatusForChat(null).stream()
                    .mapToLong(TaskStatusCount::count).sum();
            sb.append("Company-wide tasks: ").append(companyTasks)
                    .append(", of which overdue: ")
                    .append(taskRepository.countOverdueForChat(
                            null, CLOSED_TASK_STATUSES, OffsetDateTime.now()))
                    .append("\nYou may offer the team's overdue tasks.\n");
        }
    }

    /** Team-wide aggregates across all sales reps. Caller must check {@link #canSeeAllData}. */
    public String teamSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        StringBuilder sb = new StringBuilder("== Whole sales team summary ==\n");

        List<DealStatusAggregate> dealAgg = dealRepository.aggregateByStatusForChat(null);
        long dealTotal = dealAgg.stream().mapToLong(DealStatusAggregate::count).sum();
        sb.append("Deals: total ").append(dealTotal)
                .append(" (open ").append(countOf(dealAgg, DealStatus.OPEN))
                .append(", won ").append(countOf(dealAgg, DealStatus.WON))
                .append(", lost ").append(countOf(dealAgg, DealStatus.LOST)).append(")")
                .append(", WON value ").append(valueOf(dealAgg, DealStatus.WON))
                .append(", pipeline value (OPEN) ").append(valueOf(dealAgg, DealStatus.OPEN))
                .append("\n");

        long leadTotal = leadRepository.countByStatusForChat(null).stream()
                .mapToLong(LeadStatusCount::count).sum();
        sb.append("Leads: total ").append(leadTotal).append("\n");

        long taskTotal = taskRepository.countByStatusForChat(null).stream()
                .mapToLong(TaskStatusCount::count).sum();
        sb.append("Tasks: total ").append(taskTotal)
                .append(", overdue ")
                .append(taskRepository.countOverdueForChat(null, CLOSED_TASK_STATUSES, now))
                .append("\n");

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

    private static long countOf(List<DealStatusAggregate> agg, DealStatus status) {
        return agg.stream().filter(a -> a.status() == status)
                .mapToLong(DealStatusAggregate::count).sum();
    }

    private static BigDecimal valueOf(List<DealStatusAggregate> agg, DealStatus status) {
        return agg.stream().filter(a -> a.status() == status)
                .map(DealStatusAggregate::revenueOrZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private static String nullToDash(String s) {
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
