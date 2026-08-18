package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                                      String sortBy, String sortDir, String scope,
                                      Boolean unassigned, int page, int size) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);

        // Parsed and validated in one place, shared with GetLeadStatsUseCase so the tiles above the
        // table can never describe a different set of leads than the rows in it.
        LeadFilterParams filters =
                LeadFilterParams.parse(search, status, source, isCorporate, dateFrom, dateTo, unassigned);

        // Owner-scoping: SALES is restricted to their own leads; MANAGER/ADMIN see all (unscoped);
        // other roles are rejected (403). A null ownerId means "no restriction".
        UserEntity currentUser = leadAccessPolicy.currentUser();
        UUID ownerId = leadAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (ownerId == null);

        // scope only matters for a scoped (SALES) caller: "created" → leads they created,
        // anything else (default "assigned") → leads assigned to them. Ignored when unscoped.
        boolean createdByMe = "created".equalsIgnoreCase(scope);

        // "status" sorts by how much attention a lead still needs, not alphabetically —
        // see LeadSpecification.PRIORITY. Always high→low; the only ordering the UI offers.
        Specification<LeadEntity> spec = filters.toSpecification(unscoped, ownerId, createdByMe);

        if ("status".equals(sortBy)) {
            Pageable pageable = PageRequest.of(page, size);
            return leadRepository.searchLeadsByStatusPriority(spec, pageable)
                    .map(LeadResponse::from);
        }

        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = asc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return leadRepository.searchLeads(spec, pageable).map(LeadResponse::from);
    }
}
