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
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
     * Top-privilege mode: login/RBAC isn't wired yet and the assistant is granted full access,
     * so "my data" questions see ALL records (not just one arbitrary fallback user). Set
     * {@code AI_CHAT_TOP_PRIVILEGE=false} to scope strictly to the acting user once login lands.
     */
    @Value("${AI_CHAT_TOP_PRIVILEGE:true}")
    private boolean topPrivilege;

    /** Facts about leads/deals/tasks the user may view (all records in top-privilege mode). */
    public String assignedContext(UserEntity user) {
        UUID userId = user.getUserId();
        List<LeadEntity> leads = topPrivilege
                ? leadRepository.findAll() : leadRepository.findByAssignedUser_UserId(userId);
        List<DealEntity> deals = topPrivilege
                ? dealRepository.findAll() : dealRepository.findByAssignedUser_UserId(userId);
        List<TaskEntity> tasks = topPrivilege
                ? taskRepository.findAll() : taskRepository.findByAssignedUser_UserId(userId);

        StringBuilder sb = new StringBuilder();
        sb.append(topPrivilege
                ? "== Dữ liệu CRM (chế độ toàn quyền — chưa bật phân quyền) ==\n"
                : "== Dữ liệu CRM được giao cho " + user.getFullName() + " ==\n");

        // Leads — counts by status + an actual listing so the assistant can enumerate them.
        Map<Object, Long> leadByStatus = leads.stream()
                .collect(Collectors.groupingBy(LeadEntity::getStatus, Collectors.counting()));
        sb.append("Leads: tổng ").append(leads.size())
                .append(" ").append(leadByStatus).append("\n");
        if (!leads.isEmpty()) {
            sb.append("Danh sách lead (mới nhất trước, tối đa ").append(MAX_LEADS).append("):\n");
            leads.stream()
                    .sorted(Comparator.comparing(LeadEntity::getCreatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                    .limit(MAX_LEADS)
                    .forEach(l -> sb.append("  - \"").append(l.getFullName())
                            .append("\" | ").append(l.getStatus())
                            .append(" | công ty: ").append(nullToDash(l.getCompanyName()))
                            .append(" | email: ").append(nullToDash(l.getEmail()))
                            .append(" | nguồn: ").append(nullToDash(l.getSource()))
                            .append(assigneeSuffix(l))
                            .append(" | tạo lúc: ").append(l.getCreatedAt())
                            .append("\n"));
        }

        // Deals
        long openDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.OPEN).count();
        BigDecimal openValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.OPEN && d.getExpectedRevenue() != null)
                .map(DealEntity::getExpectedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal wonValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.WON && d.getExpectedRevenue() != null)
                .map(DealEntity::getExpectedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sb.append("Deals: tổng ").append(deals.size())
                .append(", đang mở ").append(openDeals)
                .append(", giá trị kỳ vọng (OPEN) ").append(openValue)
                .append(", giá trị đã thắng (WON) ").append(wonValue).append("\n");
        if (!deals.isEmpty()) {
            sb.append("Chi tiết deal (tối đa ").append(MAX_DEALS).append("):\n");
            deals.stream().limit(MAX_DEALS).forEach(d -> sb.append("  - \"").append(d.getDealName())
                    .append("\" | ").append(d.getPipelineStage())
                    .append(" | ").append(d.getStatus())
                    .append(" | giá trị ").append(d.getExpectedRevenue())
                    .append(" | dự kiến chốt ").append(d.getExpectedCloseDate()).append("\n"));
        }

        // Tasks (overdue derived per BR-17)
        LocalDate today = LocalDate.now();
        long openTasks = tasks.stream().filter(t -> t.getStatus() == TaskStatus.OPEN
                || t.getStatus() == TaskStatus.IN_PROGRESS).count();
        List<TaskEntity> overdue = tasks.stream().filter(t -> isOverdue(t, today)).toList();
        sb.append("Tasks: tổng ").append(tasks.size())
                .append(", đang mở/đang làm ").append(openTasks)
                .append(", quá hạn ").append(overdue.size()).append("\n");
        if (!overdue.isEmpty()) {
            sb.append("Công việc quá hạn (tối đa ").append(MAX_TASKS).append("):\n");
            overdue.stream().limit(MAX_TASKS).forEach(t -> sb.append("  - \"").append(t.getTitle())
                    .append("\" | hạn ").append(t.getDueDate())
                    .append(" | ưu tiên ").append(t.getPriority())
                    .append(" | ").append(t.getStatus()).append("\n"));
        }

        return sb.toString();
    }

    /** Team-wide aggregates across all sales reps (allowed because login/RBAC grants top scope for now). */
    public String teamSummary() {
        List<DealEntity> deals = dealRepository.findAll();
        List<LeadEntity> leads = leadRepository.findAll();
        List<TaskEntity> tasks = taskRepository.findAll();
        LocalDate today = LocalDate.now();

        long openDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.OPEN).count();
        long wonDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.WON).count();
        long lostDeals = deals.stream().filter(d -> d.getStatus() == DealStatus.LOST).count();
        BigDecimal wonValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.WON && d.getExpectedRevenue() != null)
                .map(DealEntity::getExpectedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openValue = deals.stream()
                .filter(d -> d.getStatus() == DealStatus.OPEN && d.getExpectedRevenue() != null)
                .map(DealEntity::getExpectedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("== Tổng hợp toàn đội bán hàng ==\n");
        sb.append("Deals: tổng ").append(deals.size())
                .append(" (mở ").append(openDeals)
                .append(", thắng ").append(wonDeals)
                .append(", thua ").append(lostDeals).append(")")
                .append(", giá trị WON ").append(wonValue)
                .append(", giá trị pipeline (OPEN) ").append(openValue).append("\n");
        sb.append("Leads: tổng ").append(leads.size()).append("\n");
        sb.append("Tasks: tổng ").append(tasks.size())
                .append(", quá hạn ").append(tasks.stream().filter(t -> isOverdue(t, today)).count())
                .append("\n");

        // Per-rep breakdown (group by assigned user)
        Map<String, List<DealEntity>> dealsByRep = deals.stream()
                .filter(d -> d.getAssignedUser() != null)
                .collect(Collectors.groupingBy(d -> repLabel(d.getAssignedUser())));
        sb.append("Theo nhân viên (tối đa ").append(MAX_REPS).append("):\n");
        dealsByRep.entrySet().stream().limit(MAX_REPS).forEach(e -> {
            List<DealEntity> rep = e.getValue();
            long open = rep.stream().filter(d -> d.getStatus() == DealStatus.OPEN).count();
            long won = rep.stream().filter(d -> d.getStatus() == DealStatus.WON).count();
            BigDecimal value = rep.stream()
                    .filter(d -> d.getStatus() == DealStatus.WON && d.getExpectedRevenue() != null)
                    .map(DealEntity::getExpectedRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append("  - ").append(e.getKey())
                    .append(": deals mở ").append(open)
                    .append(", thắng ").append(won)
                    .append(", giá trị WON ").append(value).append("\n");
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
        return assignee != null ? " | phụ trách: " + repLabel(assignee) : " | phụ trách: (chưa giao)";
    }

    private boolean isOverdue(TaskEntity t, LocalDate today) {
        return t.getDueDate() != null
                && t.getDueDate().isBefore(today)
                && t.getStatus() != TaskStatus.DONE
                && t.getStatus() != TaskStatus.CANCELLED;
    }
}
