package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** UC-22.2 — View Arrival Handover Detail (Front Office). */
@Service
@RequiredArgsConstructor
public class GetArrivalHandoverDetailUseCase {

    private final OpHandoverRepository opHandoverRepository;

    @Transactional(readOnly = true)
    public ArrivalHandoverResponse execute(UUID handoverId) {
        OpHandoverEntity handover = opHandoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Arrival handover", handoverId));

        // A DRAFT handover hasn't been submitted to Front Office yet — hide its existence.
        if (handover.getStatus() == HandoverStatus.DRAFT) {
            throw new ResourceNotFoundException("Arrival handover", handoverId);
        }

        return ArrivalHandoverResponse.from(handover);
    }
}
