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

@Service
@RequiredArgsConstructor
public class GetLeadListUseCase {

    private final LeadRepository leadRepository;

    @Transactional(readOnly = true)
    public Page<LeadResponse> execute(String search, String status, String source, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        String searchParam = StringUtils.hasText(search) ? search.trim() : null;
        LeadStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                statusParam = LeadStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        String sourceParam = StringUtils.hasText(source) ? source.trim() : null;

        return leadRepository.searchLeads(searchParam, statusParam, sourceParam, pageable)
                .map(LeadResponse::from);
    }
}
