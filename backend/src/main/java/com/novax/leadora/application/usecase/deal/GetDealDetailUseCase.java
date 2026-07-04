package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetDealDetailUseCase {

    private final DealRepository dealRepository;
    private final DealMapper dealMapper;
    private final DealAccessPolicy dealAccessPolicy;

    @Transactional(readOnly = true)
    public DealResponse execute(UUID id) {
        DealEntity deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));
        dealAccessPolicy.assertCanView(dealAccessPolicy.currentUser(), deal);
        return dealMapper.mapToResponse(deal);
    }
}
