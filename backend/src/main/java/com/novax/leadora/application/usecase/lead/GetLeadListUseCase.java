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
                                      String sortBy, String sortDir, int page, int size) {
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

        // Calendar dates ("YYYY-MM-DD") → an inclusive [from 00:00, to 23:59:59.999] UTC window.
        // The repository compares with COALESCE(:param, createdAt), so a null bound = no limit.
        OffsetDateTime dateFromParam = parseStartOfDay(dateFrom);
        OffsetDateTime dateToParam = parseEndOfDay(dateTo);

        // Owner-scoping: SALES is restricted to their own leads; MANAGER/ADMIN see all (unscoped);
        // other roles are rejected (403). A null ownerId means "no restriction".
        UserEntity currentUser = leadAccessPolicy.currentUser();
        UUID ownerId = leadAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (ownerId == null);

        // "status" sorts by pipeline priority (Converted → … → New), not alphabetically.
        // Always high→low — the only ordering the UI offers for status.
        if ("status".equals(sortBy)) {
            Pageable pageable = PageRequest.of(page, size);
            return leadRepository.searchLeadsByStatusPriority(
                            searchParam, statusParam, sourceParam, isCorporate, dateFromParam, dateToParam, unscoped, ownerId, pageable)
                    .map(LeadResponse::from);
        }

        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = asc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return leadRepository.searchLeads(
                        searchParam, statusParam, sourceParam, isCorporate, dateFromParam, dateToParam, unscoped, ownerId, pageable)
                .map(LeadResponse::from);
    }

    private static OffsetDateTime parseStartOfDay(String date) {
        if (!StringUtils.hasText(date)) return null;
        try {
            return LocalDate.parse(date.trim()).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null; // ignore malformed dates rather than failing the whole list
        }
    }

    private static OffsetDateTime parseEndOfDay(String date) {
        if (!StringUtils.hasText(date)) return null;
        try {
            return LocalDate.parse(date.trim()).atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }
}
