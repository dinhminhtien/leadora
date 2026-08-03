package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.infrastructure.persistence.specification.OpHandoverSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** UC-22.1 — View Arrival Handover List (Front Office). */
@Service
@RequiredArgsConstructor
public class GetArrivalHandoverListUseCase {


    private final OpHandoverRepository opHandoverRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ArrivalDeskScope arrivalDeskScope;

    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public Page<ArrivalHandoverResponse> execute(String search, String readinessStatus, String arrivalDate,
                                                 String assignedFoUserId, boolean deskWide,
                                                 String sortBy, String sortDir, int page, int size) {
        // Fallback matches the controller's default: soonest arrival first. (The operational list
        // keeps createdAt — that one is a log of what Sales wrote, not a queue of who is arriving.)
        Pageable pageable = HandoverListQuery.pageable(
                sortBy, sortDir, page, size, HandoverListQuery.SORTABLE, "arrivalDate");

        ReadinessStatus readinessFilter =
                HandoverListQuery.enumFilter(ReadinessStatus.class, readinessStatus, "readinessStatus");
        LocalDate arrivalFilter = HandoverListQuery.dateFilter(arrivalDate, "arrivalDate");
        // Validated before the role check: a malformed UUID is a client bug whoever sent it, and
        // reporting it only to supervisors would make the API behave differently for the same input.
        UUID requestedAssignee = HandoverListQuery.uuidFilter(assignedFoUserId, "assignedFoUserId");

        UserEntity caller = currentUserProvider.resolve(null);
        // UC-22.1 step 3 — resolved from the authenticated user, never from a request parameter.
        UUID scopedTo = arrivalDeskScope.scopeFor(caller, deskWide);
        // UC-22.1 step 5 — filtering by an arbitrary colleague is a supervisor capability. Ignored
        // (not rejected) for Front Office Staff: the scope above already answers what they may see,
        // and a 403 on a filter parameter would be a needlessly hostile way to say so.
        UUID assigneeFilter = arrivalDeskScope.canFilterByAssignee(caller) ? requestedAssignee : null;

        Specification<OpHandoverEntity> spec = OpHandoverSpecification.forFrontOffice(
                search, readinessFilter, arrivalFilter, assigneeFilter, scopedTo);

        Page<OpHandoverEntity> handovers = opHandoverRepository.findAll(spec, pageable);

        // Batch-load room/service lines for the page to build the summary without N+1.
        List<UUID> bookingIds = handovers.getContent().stream()
                .map(h -> h.getBooking() != null ? h.getBooking().getBookingId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        final Map<UUID, List<BookingDetailEntity>> detailsByBooking = bookingIds.isEmpty()
                ? Collections.emptyMap()
                : bookingDetailRepository.findByBooking_BookingIdIn(bookingIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getBooking().getBookingId()));

        // Batch-load the FO assignee names for the page too — `assigned_fo_user_id` is a scalar
        // column, so resolving each name lazily would be one extra query per row.
        List<UUID> foUserIds = handovers.getContent().stream()
                .map(OpHandoverEntity::getAssignedFoUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        final Map<UUID, String> foNamesById = foUserIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(foUserIds).stream()
                        .filter(u -> u.getFullName() != null)
                        .collect(Collectors.toMap(UserEntity::getUserId, UserEntity::getFullName));

        return handovers.map(h -> {
            UUID bookingId = h.getBooking() != null ? h.getBooking().getBookingId() : null;
            List<BookingDetailEntity> details = detailsByBooking.getOrDefault(bookingId, Collections.emptyList());
            String foName = h.getAssignedFoUserId() != null
                    ? foNamesById.get(h.getAssignedFoUserId()) : null;
            return ArrivalHandoverResponse.fromList(h, details, foName);
        });
    }

}
