package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.application.usecase.chat.dto.ChatCounts;
import com.novax.leadora.application.usecase.chat.dto.RepDealStat;
import com.novax.leadora.application.usecase.chat.dto.RepLeadCount;
import com.novax.leadora.application.usecase.chat.dto.StatusBucket;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.application.usecase.chat.time.ChatClock;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
import com.novax.leadora.application.usecase.inventory.NightAvailability;
import com.novax.leadora.application.usecase.inventory.RoomAvailabilityService;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.ChatAggregateRepository;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final int MAX_SLA = 10;
    private static final int MAX_FEEDBACK = 8;
    private static final int MAX_REPS = 20;

    /**
     * How much of a customer's comment reaches the model.
     *
     * <p>Long enough to carry a complaint, short enough that a single comment cannot crowd out
     * the rest of the snapshot — and, since this is the one field an outsider writes, short
     * enough that nothing elaborate fits inside it.
     */
    private static final int MAX_COMMENT_CHARS = 200;

    /** How many staff members to name when suggesting whose records to ask about instead. */
    private static final int MAX_SUGGESTED_REPS = 6;

    /** How many staff members named in one question get their own scoped snapshot. */
    private static final int MAX_MENTIONED_STAFF = 3;

    /**
     * A folded name shorter than this is too generic to trust as a mention: with diacritics
     * stripped, a name like "An" would fire on the Vietnamese word "an" in ordinary sentences.
     */
    private static final int MIN_MENTION_CHARS = 5;

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
    private final UserRepository userRepository;
    private final ProductServiceRepository productServiceRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final SalesFeedbackRepository feedbackRepository;

    /**
     * Roles allowed to see ALL CRM records via chat. Any other role is scoped to their own assigned
     * records. Optionally widened by {@code AI_CHAT_TOP_PRIVILEGE} (dev escape hatch).
     */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");

    /** Fixed forward window for allotment. Deliberately not derived from the question. */
    private static final int AVAILABILITY_LOOKAHEAD_NIGHTS = 14;

    /** Dates named per room type before the rest are summarised away. */
    private static final int MAX_AVAILABILITY_DATES = 4;

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    @Value("${AI_CHAT_TOP_PRIVILEGE:false}")
    private boolean topPrivilege;

    /** Business calendar, so "today" means the company's day rather than the server's. */
    private final ChatClock clock;

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
    public String scopedSnapshot(ChatActor actor, Set<CrmArea> areas, ChatDateRange range) {
        boolean all = canSeeAllData(actor);
        return snapshot(actor, all ? null : actor.userId(), areas, range,
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
    public String personalSnapshot(ChatActor actor, Set<CrmArea> areas, ChatDateRange range) {
        return snapshot(actor, actor.userId(), areas, range,
                "== CRM data assigned personally to " + actor.fullName() + " ==");
    }

    /**
     * Snapshot scoped to a staff member <b>named in the question</b> — "deal của Tiến Đinh" asked
     * by a Manager. Returns {@code ""} when it does not apply, and the caller falls back to the
     * ordinary scope.
     *
     * <p><b>Why this exists.</b> The generic snapshot lists only the newest few rows company-wide,
     * so a per-person question used to dead-end in "the listing is not filtered by assignee — use
     * the screen". Re-running the same scoped snapshot with the <em>named person's</em> id answers
     * it properly: the counts and sums are exact GROUP BY aggregates over all of that person's
     * rows, and the listing shows their records instead of everyone's.
     *
     * <p><b>BR-36:</b> only a caller allowed to read every record may be handed another person's
     * data this way; for anyone else the mention is ignored entirely.
     *
     * <p>Matching is by full name, case- and diacritic-insensitive ("tien dinh" finds "Tiến Đinh"),
     * against the current question only. A display name carrying a suffix ("Đinh Minh Tiến -
     * FSchool CT") also answers to its bare part before the dash.
     */
    public String mentionedStaffSnapshot(ChatActor actor, Set<CrmArea> areas, String query,
                                         ChatDateRange range) {
        if (!canSeeAllData(actor) || !StringUtils.hasText(query)) {
            return "";
        }
        String foldedQuery = fold(query);
        StringBuilder sb = new StringBuilder();
        int matched = 0;
        // The users table is small (staff, not customers), so scanning it in memory per turn is
        // cheaper than any fuzzy-match SQL, and keeps the diacritic folding in one place.
        for (UserEntity u : userRepository.findAllWithRole()) {
            if (matched >= MAX_MENTIONED_STAFF) {
                break;
            }
            if (u.getFullName() == null || !nameMentioned(foldedQuery, u.getFullName())) {
                continue;
            }
            sb.append(snapshot(actor, u.getUserId(), areas, range,
                    "== CRM data assigned to " + u.getFullName()
                            + " (staff member named in the question) =="));
            matched++;
        }
        return sb.toString();
    }

    /** True when the folded question contains the folded full name, or its part before " - ". */
    private static boolean nameMentioned(String foldedQuery, String fullName) {
        String folded = fold(fullName);
        if (folded.length() >= MIN_MENTION_CHARS && foldedQuery.contains(folded)) {
            return true;
        }
        String base = folded.split(" - ", 2)[0].trim();
        return !base.equals(folded)
                && base.length() >= MIN_MENTION_CHARS
                && foldedQuery.contains(base);
    }

    /** Lower-cases, strips Vietnamese diacritics and collapses spaces: "Tiến  Đinh" → "tien dinh". */
    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")   // combining marks (the tone/vowel diacritics)
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')           // đ carries a stroke, not a combining mark
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String snapshot(ChatActor actor, UUID scopeUserId, Set<CrmArea> areas,
                            ChatDateRange range, String header) {
        OffsetDateTime now = clock.now();
        OffsetDateTime from = range.start(clock.zone());
        OffsetDateTime to = range.end(clock.zone());
        // Every area's counts in one round trip. Fetching them area by area cost more in network
        // latency to a remote database than the model spent producing its first token.
        ChatCounts counts = chatAggregateRepository.countAll(scopeUserId, from, to);
        StringBuilder sb = new StringBuilder(header).append("\n");
        sb.append(periodLine(range));
        List<CrmArea> emptyAreas = new ArrayList<>();

        for (CrmArea area : CrmArea.values()) {
            boolean detail = areas.contains(area);
            long total = switch (area) {
                case LEADS -> appendLeads(sb, counts, scopeUserId, detail, from, to);
                case DEALS -> appendDeals(sb, counts, scopeUserId, detail, from, to);
                case TASKS -> appendTasks(sb, counts, scopeUserId, detail, now, from, to);
                case QUOTATIONS -> appendQuotations(sb, counts, scopeUserId, detail, from, to);
                case BOOKINGS -> appendBookings(sb, counts, scopeUserId, detail, from, to);
                case PAYMENTS -> appendPayments(sb, counts, scopeUserId, detail, from, to);
                case CUSTOMERS -> appendCustomers(sb, counts, scopeUserId, detail, from, to);
                case SLA -> appendSla(sb, counts, scopeUserId, detail, from, to);
                // Reference data owned by the hotel, not per-user rows: it carries no period of
                // its own and builds its figures from a forward window, so no date bounds here.
                case ROOM_AVAILABILITY -> appendRoomAvailability(sb, detail);
                case FEEDBACK -> appendFeedback(sb, counts, scopeUserId, detail, from, to);
            };
            // Guidance is only worth giving for what was actually asked about: an empty payments
            // area is not interesting when the question was about leads.
            if (total == 0 && detail) {
                emptyAreas.add(area);
            }
        }

        if (!emptyAreas.isEmpty()) {
            appendAffordances(sb, actor, scopeUserId, emptyAreas, range);
        }
        return sb.toString();
    }

    /**
     * States the window every figure below was computed over, and on which column.
     *
     * <p>Without it a filtered snapshot is indistinguishable from an unfiltered one: the model would
     * receive "Leads: total 3" for a question about today and have no way to know whether that is
     * three leads today or three leads ever. Naming the column matters just as much — "created
     * today" and "paid today" are different questions, and the assistant has to be able to say which
     * one it answered.
     */
    private static String periodLine(ChatDateRange range) {
        if (range.isAllTime()) {
            return "Period: ALL TIME (no date filter was applied to this question).\n";
        }
        return "Period: " + range.label() + " — every count, total and listing below covers ONLY "
                + "records whose creation date falls in " + range.from() + " .. " + range.to()
                + " (inclusive). These are NOT all-time figures. State the period in your answer.\n";
    }

    /**
     * How much of the hotel's allocation is left over the next couple of weeks.
     *
     * <p><b>Only when the question asks for it.</b> Unlike the other areas this has no per-user
     * rows and no cheap count, so there is nothing worth carrying on every turn.
     *
     * <p><b>Summarised, never listed night by night.</b> Allotment is commercially sensitive to
     * the hotel — it is the block they released to one channel — and a forward calendar dumped
     * into a chat transcript is the easiest way for it to leave the building. A short window and
     * one line per room type answers "is there a Deluxe free next week?" without ever making
     * "list the next six months" a question this has an answer to. The window is fixed here, not
     * taken from the question, so no phrasing can widen it.
     *
     * <p>The caveat in the header is not decoration. These are rooms allocated <em>to us</em>;
     * running out means our block is spent, not that the hotel is full, and an assistant that
     * blurs the two would have reps turning away business the hotel could service.
     */
    private long appendRoomAvailability(StringBuilder sb, boolean detail) {
        if (!detail) {
            return 0;
        }
        List<ProductServiceEntity> rooms = productServiceRepository.findByCategory(ProductCategory.ROOM)
                .stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .toList();
        if (rooms.isEmpty()) {
            return 0;
        }

        LocalDate from = LocalDate.now();
        LocalDate toExclusive = from.plusDays(AVAILABILITY_LOOKAHEAD_NIGHTS);
        Map<UUID, List<NightAvailability>> byRoom =
                roomAvailabilityService.nights(rooms, from, toExclusive);

        sb.append("Room availability (rooms the hotel allocated to us to sell over the next ")
                .append(AVAILABILITY_LOOKAHEAD_NIGHTS)
                .append(" nights - NOT the hotel's own vacancy. \"None left\" means our allocation is")
                .append(" spent; the Reservation team can often obtain more):\n");

        for (ProductServiceEntity room : rooms) {
            List<NightAvailability> nights = byRoom.getOrDefault(room.getProductId(), List.of());
            int open = 0;
            List<String> soldOut = new ArrayList<>();
            List<String> closed = new ArrayList<>();
            int unpublished = 0;
            boolean stale = false;

            for (NightAvailability night : nights) {
                if (night.closed()) {
                    closed.add(DAY_MONTH.format(night.date()));
                } else if (!night.published()) {
                    unpublished++;
                } else {
                    stale |= night.stale();
                    if (night.available() != null && night.available() > 0) {
                        open++;
                    } else {
                        soldOut.add(DAY_MONTH.format(night.date()));
                    }
                }
            }

            // Denominator is the nights we actually have an answer for, not the whole window.
            // Counting unpublished and closed nights against it reported "free on 3 of the next
            // 14" for a room type whose quota simply had not been published yet — reading as
            // scarcity, which is precisely the confusion the rest of this method exists to
            // prevent. Those nights are reported separately below, in their own words.
            int answered = open + soldOut.size();
            sb.append("  - ").append(room.getName())
                    .append(": rooms free on ").append(open)
                    .append(" of ").append(answered)
                    .append(answered == 1 ? " night" : " nights").append(" with published allocation");
            if (!soldOut.isEmpty()) {
                sb.append("; none left ").append(String.join(", ", capped(soldOut)));
            }
            if (!closed.isEmpty()) {
                sb.append("; hotel not selling ").append(String.join(", ", capped(closed)));
            }
            if (unpublished > 0) {
                // Said explicitly because the model would otherwise read a missing number as a
                // zero, and report an unpublished fortnight as a sold-out one.
                sb.append("; ").append(unpublished)
                        .append(" night(s) have no allocation published yet (not the same as sold out)");
            }
            if (stale) {
                sb.append("; figures not reconciled with the hotel recently");
            }
            sb.append("\n");
        }
        return rooms.size();
    }

    /** Keeps a date list to a readable handful rather than spelling out a fortnight. */
    private static List<String> capped(List<String> dates) {
        if (dates.size() <= MAX_AVAILABILITY_DATES) {
            return dates;
        }
        List<String> shown = new ArrayList<>(dates.subList(0, MAX_AVAILABILITY_DATES));
        shown.add("+" + (dates.size() - MAX_AVAILABILITY_DATES) + " more");
        return shown;
    }

    // ── Per-area sections ─────────────────────────────────────────────────────
    // Each returns the area's row count so the caller can spot empty areas.

    private long appendLeads(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                             OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.LEADS);
        sb.append("Leads: total ").append(total)
                .append(statusBreakdown(counts.of(CrmArea.LEADS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Lead list, newest first", Math.min(total, MAX_LEADS), total, CrmArea.LEADS));
            leadRepository.findRecentForChat(scope, from, to, page(MAX_LEADS)).forEach(l ->
                    sb.append("  - \"").append(l.getFullName())
                            .append("\" | ").append(l.getStatus())
                            .append(" | company: ").append(dash(l.getCompanyName()))
                            .append(" | email: ").append(dash(l.getEmail()))
                            .append(" | source: ").append(dash(l.getSource()))
                            .append(" | assigned to: ").append(assigneeLabel(l.getAssignedUser()))
                            .append(" | created: ").append(ts(l.getCreatedAt())).append("\n"));
        }
        return total;
    }

    private long appendDeals(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                             OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.DEALS);
        sb.append("Deals: total ").append(total)
                .append(", open ").append(counts.count(CrmArea.DEALS, DealStatus.OPEN.name()))
                .append(", expected value (OPEN) ")
                .append(counts.amount(CrmArea.DEALS, DealStatus.OPEN.name()))
                .append(", won value (WON) ")
                .append(counts.amount(CrmArea.DEALS, DealStatus.WON.name())).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Deal details, newest first", Math.min(total, MAX_DEALS), total, CrmArea.DEALS));
            dealRepository.findRecentForChat(scope, from, to, page(MAX_DEALS)).forEach(d ->
                    sb.append("  - \"").append(d.getDealName())
                            .append("\" | ").append(d.getPipelineStage())
                            .append(" | ").append(d.getStatus())
                            .append(" | value ").append(d.getExpectedRevenue())
                            .append(" | expected close ").append(d.getExpectedCloseDate())
                            .append(" | assigned to: ").append(assigneeLabel(d.getAssignedUser()))
                            .append("\n"));
            // Full company scope only: exact per-person aggregates, so "how much does X hold?"
            // is answerable even though the listing above is capped and unfiltered.
            if (scope == null) {
                appendPerRepDealStats(sb, from, to);
            }
        }
        return total;
    }

    /**
     * One line of exact deal aggregates per staff member (GROUP BY, covers ALL their deals). The
     * capped listing cannot answer per-person totals; this can, so per-person questions no longer
     * have to be deflected to the Deals screen.
     */
    private void appendPerRepDealStats(StringBuilder sb, OffsetDateTime from, OffsetDateTime to) {
        List<RepDealStat> stats = dealRepository.statsPerAssignee(from, to);
        List<String> reps = stats.stream().map(RepDealStat::repName).distinct().limit(MAX_REPS).toList();
        if (reps.isEmpty()) {
            return;
        }
        sb.append("Deals per staff member (EXACT aggregates over all their deals, up to ")
                .append(MAX_REPS).append(" people):\n");
        for (String rep : reps) {
            List<RepDealStat> forRep = stats.stream().filter(s -> rep.equals(s.repName())).toList();
            sb.append("  - ").append(rep)
                    .append(": open ").append(repCount(forRep, DealStatus.OPEN))
                    .append(" worth ").append(repValue(forRep, DealStatus.OPEN))
                    .append(", won ").append(repCount(forRep, DealStatus.WON))
                    .append(" worth ").append(repValue(forRep, DealStatus.WON))
                    .append(", lost ").append(repCount(forRep, DealStatus.LOST))
                    .append("\n");
        }
    }

    private long appendTasks(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                             OffsetDateTime now, OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.TASKS);
        long open = counts.count(CrmArea.TASKS, TaskStatus.OPEN.name());
        sb.append("Tasks: total ").append(total)
                .append(", open/in progress ").append(open)
                .append(", overdue ").append(counts.overdueTasks())
                .append("\n");

        if (detail && open > 0) {
            sb.append(listingHeader("Open tasks, earliest deadline first", Math.min(open, MAX_TASKS), open, CrmArea.TASKS));
            taskRepository.findOpenForChat(scope, CLOSED_TASK_STATUSES, from, to, page(MAX_TASKS)).forEach(t ->
                    sb.append("  - \"").append(t.getTitle())
                            .append("\" | due ").append(ts(t.getEndAt()))
                            .append(" | priority ").append(t.getPriority())
                            .append(" | ").append(t.getStatus())
                            .append(isOverdue(t, now) ? " | OVERDUE" : "").append("\n"));
        }
        return total;
    }

    private long appendQuotations(StringBuilder sb, ChatCounts counts, UUID scope,
                                  boolean detail, OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.QUOTATIONS);
        sb.append("Quotations: total ").append(total)
                .append(valueBreakdown(counts.of(CrmArea.QUOTATIONS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Quotation details, newest first", Math.min(total, MAX_QUOTATIONS), total, CrmArea.QUOTATIONS));
            quotationRepository.findRecentForChat(scope, from, to, page(MAX_QUOTATIONS)).forEach(q ->
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

    private long appendBookings(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                                OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.BOOKINGS);
        sb.append("Bookings: total ").append(total)
                .append(valueBreakdown(counts.of(CrmArea.BOOKINGS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Booking details, newest first", Math.min(total, MAX_BOOKINGS), total, CrmArea.BOOKINGS));
            bookingRepository.findRecentForChat(scope, from, to, page(MAX_BOOKINGS)).forEach(b ->
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

    private long appendPayments(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                                OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.PAYMENTS);
        sb.append("Payments: total ").append(total)
                .append(", PAID amount ").append(counts.amount(CrmArea.PAYMENTS, "PAID"))
                .append(", PENDING amount ").append(counts.amount(CrmArea.PAYMENTS, "PENDING"))
                .append(statusBreakdown(counts.of(CrmArea.PAYMENTS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Payment details, newest first", Math.min(total, MAX_PAYMENTS), total, CrmArea.PAYMENTS));
            paymentRepository.findRecentForChat(scope, from, to, page(MAX_PAYMENTS)).forEach(p ->
                    sb.append("  - booking ")
                            .append(p.getBooking() != null ? p.getBooking().getBookingCode() : "-")
                            .append(" | ").append(p.getPaymentType())
                            .append(" | ").append(p.getStatus())
                            .append(" | amount ").append(p.getAmount())
                            .append(" | due ").append(p.getDueDate())
                            .append(" | paid at ").append(ts(p.getPaidAt())).append("\n"));
        }
        return total;
    }

    private long appendCustomers(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                                 OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.CUSTOMERS);
        sb.append("Customers: total ").append(total)
                .append(statusBreakdown(counts.of(CrmArea.CUSTOMERS))).append("\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Customer list, newest first", Math.min(total, MAX_CUSTOMERS), total, CrmArea.CUSTOMERS));
            customerRepository.findRecentForChat(scope, from, to, page(MAX_CUSTOMERS)).forEach(c ->
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
     * SLA tracking rows — the table the breach scheduler maintains and the SLA Control screen
     * shows, so the assistant and the screen agree. (The older {@code sla_records} table is not
     * kept up to date by anything and is deliberately not read here.)
     *
     * <p>A tracking row references its subject polymorphically and has no assignee column, so
     * ownership — and therefore BR-36 — is resolved by joining whichever parent it points at;
     * that happens in SQL, in {@link ChatAggregateRepository}, for both the counts and the rows.
     *
     * <p>Like tasks, the counts cover every row but the listing shows only what is still worth
     * acting on: a RESOLVED row is history. The header therefore reports the unresolved count, so
     * "showing 10 of 12" never quietly means "10 of 12 including the closed ones".
     */
    private long appendSla(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                           OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.SLA);
        long active = counts.count(CrmArea.SLA, SlaStatus.ACTIVE.name());
        long breached = counts.count(CrmArea.SLA, SlaStatus.BREACHED.name());
        long unresolved = active + breached;

        sb.append("SLA records: total ").append(total)
                .append(", active ").append(active)
                .append(", breached ").append(breached)
                .append(statusBreakdown(counts.of(CrmArea.SLA))).append("\n");

        if (detail && unresolved > 0) {
            sb.append(listingHeader("Unresolved SLA records, earliest deadline first",
                    Math.min(unresolved, MAX_SLA), unresolved, CrmArea.SLA));
            chatAggregateRepository.unresolvedSla(scope, from, to, MAX_SLA).forEach(s ->
                    sb.append("  - ").append(s.activityType())
                            .append(" on ").append(s.entityType())
                            .append(" | ").append(s.status())
                            .append(" | deadline ").append(ts(s.deadlineAt()))
                            // Null here is real: the subject may be unassigned, or no longer
                            // exist at all. Saying so beats a bare dash the model might read
                            // as a missing field.
                            .append(" | assigned to: ")
                            .append(s.assigneeName() != null ? s.assigneeName() : "(unassigned)")
                            .append("\n"));
        }
        return total;
    }

    /**
     * What customers said about the service, and how much of it nobody has looked at yet.
     *
     * <p><b>Counted on {@code submitted_at}, unlike every other area.</b> The row is created when
     * the survey link goes out; it holds an opinion only once the customer answers. The section
     * says so in its own header rather than relying on the shared {@code Period:} line, which
     * speaks about creation dates.
     *
     * <p><b>Scope, and how it relates to {@code FEEDBACK_VIEW}.</b> The Feedback screen and the
     * sentiment dashboard require that permission, which SALES does not hold; the chat section
     * does not check it, because the scope predicate has already narrowed the rows to feedback
     * <em>about the asking rep</em>. What a rep can read here is their own results and nothing
     * else — the same reasoning that gave SALES {@code REPORTING_VIEW} for their own task
     * performance. Team-wide feedback still needs {@link #canSeeAllData}, since that is the scope
     * the permission actually guards. If the business wants even a rep's own ratings gated, this
     * section is the single place to add the check.
     *
     * <p><b>Three different notions of "bad" are kept apart.</b> {@code review_status} is a
     * workflow state (has a human triaged it), the rating is the customer's own score, and the
     * ABSA sentiment columns are a model's reading of the comment. Presenting a PENDING count as
     * dissatisfaction, which a breakdown alone invites, would report our backlog as their
     * unhappiness. The low-rating figure is therefore given its own line, derived in SQL.
     */
    private long appendFeedback(StringBuilder sb, ChatCounts counts, UUID scope, boolean detail,
                                OffsetDateTime from, OffsetDateTime to) {
        long total = counts.total(CrmArea.FEEDBACK);
        sb.append("Customer feedback (submitted answers only, counted by SUBMISSION date, not "
                        + "creation date): total ").append(total)
                .append(", scored 2 or less by the customer ").append(counts.lowRatedFeedback())
                .append(averageRating(counts.of(CrmArea.FEEDBACK)))
                .append(statusBreakdown(counts.of(CrmArea.FEEDBACK)))
                .append(" (PENDING/REVIEWED/DISMISSED is OUR triage state, NOT the customer's "
                        + "opinion)\n");

        if (detail && total > 0) {
            sb.append(listingHeader("Feedback, newest submission first",
                    Math.min(total, MAX_FEEDBACK), total, CrmArea.FEEDBACK));
            feedbackRepository.findRecentForChat(scope, from, to, page(MAX_FEEDBACK)).forEach(f ->
                    sb.append("  - rating ").append(f.getRating() != null ? f.getRating() : "-")
                            .append("/5 | attitude ").append(score(f.getRatingAttitude()))
                            .append(", speed ").append(score(f.getRatingSpeed()))
                            .append(", accuracy ").append(score(f.getRatingAccuracy()))
                            .append(" | ").append(f.getReviewStatus())
                            .append(" | customer: ")
                            .append(f.getCustomer() != null ? f.getCustomer().getFullName() : "-")
                            .append(" | about staff: ").append(assigneeLabel(f.getSalesStaff()))
                            .append(" | submitted: ").append(ts(f.getSubmittedAt()))
                            .append(" | customer wrote: ").append(quotedComment(f.getComment()))
                            .append("\n"));
        }
        return total;
    }

    /** Weighted mean of the per-status averages, so it is the mean over rows, not over statuses. */
    private static String averageRating(List<StatusBucket> buckets) {
        long rated = buckets.stream().filter(b -> b.amount() != null).mapToLong(b -> b.count()).sum();
        if (rated == 0) {
            return "";
        }
        BigDecimal weighted = buckets.stream()
                .filter(b -> b.amount() != null)
                .map(b -> b.amount().multiply(BigDecimal.valueOf(b.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ", average rating " + weighted.divide(BigDecimal.valueOf(rated), 1, RoundingMode.HALF_UP)
                + "/5 over " + rated + " scored";
    }

    /**
     * A customer's words, made safe to sit inside the reference block.
     *
     * <p>This is the only text in the whole snapshot written by somebody outside the company: a
     * customer needs no account to submit feedback, only the token in their survey link. Two
     * things follow. Newlines are stripped because the block is line-structured — a comment
     * containing {@code == Company knowledge base ==} would otherwise forge a section header and
     * have the model read whatever came next as retrieved company data. And it is quoted and
     * labelled as the customer's words, so an instruction written into the box reads as a quote
     * rather than as something addressed to the assistant.
     *
     * <p>The delimiters around the whole reference block do the heavier lifting (see
     * {@code ChatLlmService}); this keeps a single field from breaking the structure inside them.
     */
    private static String quotedComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return "(no comment)";
        }
        String flattened = comment.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]+", " ").trim();
        if (flattened.length() > MAX_COMMENT_CHARS) {
            flattened = flattened.substring(0, MAX_COMMENT_CHARS) + "...(truncated)";
        }
        return "\"" + flattened.replace("\"", "'") + "\"";
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
                                   List<CrmArea> emptyAreas, ChatDateRange range) {
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
        // Same window as the scoped snapshot above: offering "company-wide leads: 20" from all time
        // when the user asked about today would answer a question they did not ask.
        ChatCounts companyWide = chatAggregateRepository.countAll(
                null, range.start(clock.zone()), range.end(clock.zone()));
        for (CrmArea area : emptyAreas) {
            appendCompanyWideFallback(sb, area, companyWide, range);
        }
    }

    /** Company-wide figures a privileged caller can be offered when their own scope is empty. */
    private void appendCompanyWideFallback(StringBuilder sb, CrmArea area, ChatCounts companyWide,
                                           ChatDateRange range) {
        sb.append("Company-wide ").append(area.name().toLowerCase()).append(": ")
                .append(companyWide.total(area));

        switch (area) {
            case LEADS -> {
                sb.append(statusBreakdown(companyWide.of(CrmArea.LEADS))).append("\n");
                List<RepLeadCount> perRep = leadRepository.countPerAssignee(
                        range.start(clock.zone()), range.end(clock.zone()), page(MAX_SUGGESTED_REPS));
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
            case SLA -> sb.append(", of which breached: ")
                    .append(companyWide.count(CrmArea.SLA, SlaStatus.BREACHED.name()))
                    .append("\nYou may offer the team's breached or active SLA records.\n");
            case ROOM_AVAILABILITY -> sb.append("\nYou may offer the team's room availability.\n");
            case FEEDBACK -> sb.append(", of which scored 2 or less: ")
                    .append(companyWide.lowRatedFeedback())
                    .append("\nYou may offer the team's customer feedback. Do NOT read an empty "
                            + "personal result as customers being satisfied with this user: it "
                            + "means none of their customers answered a survey in this period.\n");
        }
    }

    /** Team-wide aggregates across all sales reps. Caller must check {@link #canSeeAllData}. */
    public String teamSummary(ChatDateRange range) {
        OffsetDateTime from = range.start(clock.zone());
        OffsetDateTime to = range.end(clock.zone());
        // Two round trips for the whole summary: every area's counts, then the per-rep pivot.
        ChatCounts counts = chatAggregateRepository.countAll(null, from, to);
        StringBuilder sb = new StringBuilder("== Whole sales team summary ==\n");
        sb.append(periodLine(range));

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
        sb.append("SLA records: total ").append(counts.total(CrmArea.SLA))
                .append(", breached ").append(counts.count(CrmArea.SLA, SlaStatus.BREACHED.name()))
                .append("\n");

        // Per-rep breakdown: the query returns one row per (rep, status); pivot to one line per rep.
        appendPerRepDealStats(sb, from, to);
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
                .mapToLong(s -> s.count()).sum();
    }

    private static BigDecimal repValue(List<RepDealStat> stats, DealStatus status) {
        return stats.stream().filter(s -> s.status() == status)
                .map(s -> s.revenueOrZero())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
    }

    private static String dash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    /**
     * A per-aspect customer score, or a dash when they left that one blank.
     *
     * <p>Rendered as "n/a" rather than 0: the aspect ratings are optional on the feedback form,
     * and a zero would be read as the worst possible score instead of no answer.
     */
    private static String score(Short value) {
        return value == null ? "n/a" : value.toString();
    }

    /**
     * A stored timestamp, rendered in the business calendar.
     *
     * <p><b>Why this is not cosmetic.</b> These come back from the database in UTC, and the prompt
     * tells the model today's date in {@code Asia/Ho_Chi_Minh}. Printed raw, a lead created at
     * 01:13 on the 10th locally appears as {@code 2026-08-09T18:13Z}, so the model compares the two
     * and correctly concludes it was not created today — from data that was shown wrongly. The
     * error is silent, always in the same direction, and hits everything created between midnight
     * and 07:00 local, which is a seventh of all records.
     *
     * <p>The offset is kept in the output rather than trimmed: it costs a few characters and makes
     * the timestamp unambiguous against the CURRENT TIME block, which names the same zone.
     */
    private String ts(OffsetDateTime instant) {
        return instant == null ? "-" : instant.atZoneSameInstant(clock.zone()).toOffsetDateTime()
                .truncatedTo(ChronoUnit.SECONDS).toString();
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
