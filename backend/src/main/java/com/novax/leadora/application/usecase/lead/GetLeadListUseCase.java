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
    public Page<LeadResponse> execute(String search, String status, String source,
                                      String sortBy, String sortDir, int page, int size) {
        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        // Pass "" (not null) so Hibernate 6 binds as varchar instead of bytea
        String searchParam = StringUtils.hasText(search) ? search.trim() : "";
        LeadStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                statusParam = LeadStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        String sourceParam = StringUtils.hasText(source) ? source.trim() : "";

        return leadRepository.searchLeads(searchParam, statusParam, sourceParam, pageable)
                .map(LeadResponse::from);
    }
}
