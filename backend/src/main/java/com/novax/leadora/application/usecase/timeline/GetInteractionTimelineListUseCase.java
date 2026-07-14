package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.api.dto.response.InteractionTimelineResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.InteractTimelineRepository;
import com.novax.leadora.infrastructure.persistence.specification.InteractionTimelineSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetInteractionTimelineListUseCase {

    private final InteractTimelineRepository interactTimelineRepository;
    private final InteractionTimelineMapper mapper;
    private final InteractionTimelineAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public Page<InteractionTimelineResponse> execute(
            String search,
            String type,
            UUID agentId,
            String linkedType,
            UUID linkedId,
            int page,
            int size
    ) {
        UserEntity currentUser = accessPolicy.currentUser();
        UUID scopedUserId = accessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        var spec = InteractionTimelineSpecification.filter(
                search,
                type,
                agentId,
                linkedType,
                linkedId,
                unscoped,
                scopedUserId
        );

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return interactTimelineRepository.findAll(spec, pageable)
                .map(mapper::mapToResponse);
    }
}
