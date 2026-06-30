package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverSummaryResponse;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.specification.OpHandoverSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** UC-22.1 — Front Office desk summary: arrival-handover counts by readiness. */
@Service
@RequiredArgsConstructor
public class GetArrivalHandoverSummaryUseCase {

    private final OpHandoverRepository opHandoverRepository;

    @Transactional(readOnly = true)
    public ArrivalHandoverSummaryResponse execute() {
        // All handovers submitted to Front Office (status != DRAFT).
        List<OpHandoverEntity> all =
                opHandoverRepository.findAll(OpHandoverSpecification.forFrontOffice(null, null, null));

        return ArrivalHandoverSummaryResponse.builder()
                .total(all.size())
                .pendingReview(count(all, ReadinessStatus.PENDING_REVIEW))
                .reviewed(count(all, ReadinessStatus.REVIEWED))
                .readyForArrival(count(all, ReadinessStatus.READY_FOR_ARRIVAL))
                .needClarification(count(all, ReadinessStatus.NEED_CLARIFICATION))
                .build();
    }

    private long count(List<OpHandoverEntity> all, ReadinessStatus status) {
        return all.stream().filter(h -> h.getReadinessStatus() == status).count();
    }
}
