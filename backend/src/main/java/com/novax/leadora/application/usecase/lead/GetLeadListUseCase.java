package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.response.LeadResponse;
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

import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetLeadListUseCase {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "fullName", "status");

    private final LeadRepository leadRepository;

    @Transactional(readOnly = true)
    public Page<LeadResponse> execute(String search, String status, String source, Boolean isCorporate,
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

        // "status" sorts by pipeline priority (Converted → … → New), not alphabetically.
        // Always high→low — the only ordering the UI offers for status.
        if ("status".equals(sortBy)) {
            Pageable pageable = PageRequest.of(page, size);
            return leadRepository.searchLeadsByStatusPriority(searchParam, statusParam, sourceParam, isCorporate, pageable)
                    .map(LeadResponse::from);
        }

        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = asc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return leadRepository.searchLeads(searchParam, statusParam, sourceParam, isCorporate, pageable)
                .map(LeadResponse::from);
    }
}
