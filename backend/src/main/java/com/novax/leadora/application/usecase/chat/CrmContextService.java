package com.novax.leadora.application.usecase.chat;

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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step [2] of the hybrid pipeline: read-only retrieval of CRM facts, scope-enforced in code.
 *
 * <p>Data scope (BR-36) is applied with {@code WHERE assigned_user_id = ...} queries — the
 * assistant can never receive rows outside the requested scope, independent of what the LLM
 * does with the text. Output is a compact, human-readable block stuffed into the prompt.
 *
 * <p>Must be called inside a transaction (lazy associations are read here).
 */
@Service
@RequiredArgsConstructor
public class CrmContextService {

    private static final int MAX_LEADS = 25;
    private static final int MAX_DEALS = 15;
    private static final int MAX_TASKS = 10;
    private static final int MAX_REPS = 20;

    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;

    /**
     * Roles allowed to see ALL CRM records via chat. A Sales Staff (or any other role) is scoped
     * to their own assigned records only — so "show me the leads" returns just their leads, while
     * a Manager/Admin sees the whole team's. Optionally widened by {@code AI_CHAT_TOP_PRIVILEGE}
     * (dev escape hatch) — default false now that login/RBAC is wired.
     */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");

    @Value("${AI_CHAT_TOP_PRIVILEGE:false}")
    private boolean topPrivilege;

    /** Whether {@code user}'s role may read every record (team-wide), vs only their own. */
    public boolean canSeeAllData(UserEntity user) {
        if (topPrivilege) return true;
        String role = (user.getRole() != null && user.getRole().getRoleName() != null)
                ? user.getRole().getRoleName().trim().toUpperCase() : "";
        return FULL_SCOPE_ROLES.contains(role);
    }

    /** Facts about leads/deals/tasks the user may view (team-wide for Manager/Admin, else own). */
    public String assignedContext(UserEntity user) {
        UUID userId = user.getUserId();
        boolean all = canSeeAllData(user);
        List<LeadEntity> leads = all
                ? leadRepository.findAll() : leadRepository.findByAssignedUser_UserId(userId);
        List<DealEntity> deals = all
                ? dealRepository.findAll() : dealRepository.findByAssignedUser_UserId(userId);
        List<TaskEntity> tasks = all
                ? taskRepository.findAll() : taskRepository.findByAssignedUser_UserId(userId);

        StringBuilder sb = new StringBuilder();
        sb.append(all
                ? "== Full CRM data (manager access) ==\n"
                : "== CRM data assigned to " + user.getFullName() + " ==\n");

        // Leads — counts by status + an actual listing so the assistant can enumerate them.
        Map<Object, Long> leadByStatus = leads.stream()
                .collect(Collectors.groupingBy(l -> l.getStatus(), Collectors.counting()));
        sb.append("Leads: total ").append(leads.size())
                .append(" ").append(leadByStatus).append("\n");
        if (!leads.isEmpty()) {
            sb.append("Lead list (newest first, up to ").append(MAX_LEADS).append("):\n");
            leads.stream()
                    .sorted(Comparator.comparing((LeadEntity l) -> l.getCreatedAt(),
                            Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                    .limit(MAX_LEADS)
                    .forEach(l -> sb.append("  - \"").append(l.getFullName())
                            .append("\" | ").append(l.getStatus())
                            .append(" | company: ").append(nullToDash(l.getCompanyName()))
                            .append(" | email: ").append(nullToDash(l.getEmail()))
                            .append(" | source: ").append(nullToDash(l.getSource()))
                            .append(assigneeSuffix(l))
                            .append(" | created: ").append(l.getCreatedAt())
                            .append("\n"));
        }

        // Deals
        long openDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.OPEN).count();
        BigDecimal openValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.OPEN && d.getExpectedRevenue() != null)
                .map(d -> d.getExpectedRevenue())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        BigDecimal wonValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.WON && d.getExpectedRevenue() != null)
                .map(d -> d.getExpectedRevenue())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        sb.append("Deals: total ").append(deals.size())
                .append(", open ").append(openDeals)
                .append(", expected value (OPEN) ").append(openValue)
                .append(", won value (WON) ").append(wonValue).append("\n");
        if (!deals.isEmpty()) {
            sb.append("Deal details (up to ").append(MAX_DEALS).append("):\n");
            deals.stream().limit(MAX_DEALS).forEach(d -> sb.append("  - \"").append(d.getDealName())
                    .append("\" | ").append(d.getPipelineStage())
                    .append(" | ").append(d.getStatus())
                    .append(" | value ").append(d.getExpectedRevenue())
                    .append(" | expected close ").append(d.getExpectedCloseDate()).append("\n"));
        }

        // Tasks (overdue derived: status == OPEN && endAt < now())
        OffsetDateTime now = OffsetDateTime.now();
        long openTasks = tasks.stream().filter(t -> t.getStatus() == TaskStatus.OPEN).count();
        List<TaskEntity> overdue = tasks.stream().filter(t -> isOverdue(t, now)).toList();
        sb.append("Tasks: total ").append(tasks.size())
                .append(", open/in progress ").append(openTasks)
                .append(", overdue ").append(overdue.size()).append("\n");
        if (!overdue.isEmpty()) {
            sb.append("Overdue tasks (up to ").append(MAX_TASKS).append("):\n");
            overdue.stream().limit(MAX_TASKS).forEach(t -> sb.append("  - \"").append(t.getTitle())
                    .append("\" | due ").append(t.getEndAt())
                    .append(" | priority ").append(t.getPriority())
                    .append(" | ").append(t.getStatus()).append("\n"));
        }

        return sb.toString();
    }

    /** Team-wide aggregates across all sales reps (allowed because login/RBAC grants top scope for now). */
    public String teamSummary() {
        List<DealEntity> deals = dealRepository.findAll();
        List<LeadEntity> leads = leadRepository.findAll();
        List<TaskEntity> tasks = taskRepository.findAll();

        long openDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.OPEN).count();
        long wonDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.WON).count();
        long lostDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.LOST).count();
        BigDecimal wonValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.WON && d.getExpectedRevenue() != null)
                .map(d -> d.getExpectedRevenue())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        BigDecimal openValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.OPEN && d.getExpectedRevenue() != null)
                .map(d -> d.getExpectedRevenue())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        StringBuilder sb = new StringBuilder();
        sb.append("== Whole sales team summary ==\n");
        sb.append("Deals: total ").append(deals.size())
                .append(" (open ").append(openDeals)
                .append(", won ").append(wonDeals)
                .append(", thua ").append(lostDeals).append(")")
                .append(", WON value ").append(wonValue)
                .append(", pipeline value (OPEN) ").append(openValue).append("\n");
        sb.append("Leads: total ").append(leads.size()).append("\n");
        OffsetDateTime nowTeam = OffsetDateTime.now();
        sb.append("Tasks: total ").append(tasks.size())
                .append(", overdue ").append(tasks.stream().filter(t -> isOverdue(t, nowTeam)).count())
                .append("\n");

        // Per-rep breakdown (group by assigned user)
        Map<String, List<DealEntity>> dealsByRep = deals.stream()
                .filter(d -> d.getAssignedUser() != null)
                .collect(Collectors.groupingBy(d -> repLabel(d.getAssignedUser())));
        sb.append("By staff member (up to ").append(MAX_REPS).append("):\n");
        dealsByRep.entrySet().stream().limit(MAX_REPS).forEach(e -> {
            List<DealEntity> rep = e.getValue();
            long open = rep.stream().filter(d -> d.getStatus() == DealStatus.OPEN).count();
            long won = rep.stream().filter(d -> d.getStatus() == DealStatus.WON).count();
            BigDecimal value = rep.stream()
                    .filter(d -> d.getStatus() == DealStatus.WON && d.getExpectedRevenue() != null)
                    .map(d -> d.getExpectedRevenue())
                    .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
            sb.append("  - ").append(e.getKey())
                    .append(": open deals ").append(open)
                    .append(", won ").append(won)
                    .append(", WON value ").append(value).append("\n");
        });

        return sb.toString();
    }

    private String repLabel(UserEntity u) {
        return u.getFullName() != null ? u.getFullName() : u.getUserId().toString();
    }

    private String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private String assigneeSuffix(LeadEntity lead) {
        UserEntity assignee = lead.getAssignedUser();
        return assignee != null ? " | assigned to: " + repLabel(assignee) : " | assigned to: (unassigned)";
    }

    private boolean isOverdue(TaskEntity t, OffsetDateTime now) {
        if (t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED) return false;
        return t.getEndAt() != null && t.getEndAt().isBefore(now);
    }
}
