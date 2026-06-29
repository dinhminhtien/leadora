package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.specification.OpHandoverSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** UC-22.1 — View Arrival Handover List (Front Office). */
@Service
@RequiredArgsConstructor
public class GetArrivalHandoverListUseCase {

    private final OpHandoverRepository opHandoverRepository;

    @Transactional(readOnly = true)
    public Page<ArrivalHandoverResponse> execute(String search, String readinessStatus,
                                                 String sortBy, String sortDir, int page, int size) {
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        ReadinessStatus readinessFilter = parseReadiness(readinessStatus);
        Specification<OpHandoverEntity> spec = OpHandoverSpecification.forFrontOffice(search, readinessFilter);

        return opHandoverRepository.findAll(spec, pageable).map(ArrivalHandoverResponse::from);
    }

    private ReadinessStatus parseReadiness(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ReadinessStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null; // unknown filter value → no readiness constraint
        }
    }
}
