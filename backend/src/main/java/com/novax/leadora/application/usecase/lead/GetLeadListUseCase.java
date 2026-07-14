package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetLeadListUseCase {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "fullName", "status");

    private final LeadRepository leadRepository;
    private final LeadAccessPolicy leadAccessPolicy;

    @Transactional(readOnly = true)
    public Page<LeadResponse> execute(String search, String status, String source, Boolean isCorporate,
                                      String dateFrom, String dateTo,
                                      String sortBy, String sortDir, String scope, int page, int size) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);

        // Pass "" (not null) so Hibernate 6 binds as varchar instead of bytea
        String searchParam = StringUtils.hasText(search) ? search.trim() : "";
        LeadStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                statusParam = LeadStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        String sourceParam = StringUtils.hasText(source) ? source.trim() : "";

        // Date bounds. Two accepted shapes:
        //   * ISO offset datetime ("2026-07-05T17:00:00.000Z") — the client states its
        //     own day boundary as an exact instant (mobile sends local midnight
        //     converted to UTC), used as-is;
        //   * bare calendar date ("2026-07-06") — legacy shape, interpreted as an
        //     inclusive [00:00, 23:59:59.999] UTC window.
        // The repository compares with COALESCE(:param, createdAt), so a null bound = no limit.
        OffsetDateTime dateFromParam = parseStartOfDay(dateFrom);
        OffsetDateTime dateToParam = parseEndOfDay(dateTo);

        // Owner-scoping: SALES is restricted to their own leads; MANAGER/ADMIN see all (unscoped);
        // other roles are rejected (403). A null ownerId means "no restriction".
        UserEntity currentUser = leadAccessPolicy.currentUser();
        UUID ownerId = leadAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (ownerId == null);

        // scope only matters for a scoped (SALES) caller: "created" → leads they created,
        // anything else (default "assigned") → leads assigned to them. Ignored when unscoped.
        boolean createdByMe = "created".equalsIgnoreCase(scope);

        // "status" sorts by pipeline priority (Converted → … → New), not alphabetically.
        // Always high→low — the only ordering the UI offers for status.
        if ("status".equals(sortBy)) {
            Pageable pageable = PageRequest.of(page, size);
            return leadRepository.searchLeadsByStatusPriority(
                            searchParam, statusParam, sourceParam, isCorporate, dateFromParam, dateToParam, unscoped, ownerId, createdByMe, pageable)
                    .map(LeadResponse::from);
        }

        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = asc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return leadRepository.searchLeads(
                        searchParam, statusParam, sourceParam, isCorporate, dateFromParam, dateToParam, unscoped, ownerId, createdByMe, pageable)
                .map(LeadResponse::from);
    }

    private static OffsetDateTime parseStartOfDay(String date) {
        if (!StringUtils.hasText(date)) return null;
        String value = date.trim();
        OffsetDateTime exact = parseOffsetDateTime(value);
        if (exact != null) return exact; // client already chose its day boundary
        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null; // ignore malformed dates rather than failing the whole list
        }
    }

    private static OffsetDateTime parseEndOfDay(String date) {
        if (!StringUtils.hasText(date)) return null;
        String value = date.trim();
        OffsetDateTime exact = parseOffsetDateTime(value);
        if (exact != null) return exact;
        try {
            return LocalDate.parse(value).atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
