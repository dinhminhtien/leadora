package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.api.dto.response.InteractionTimelineResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.InteractTimelineRepository;
import com.novax.leadora.infrastructure.persistence.specification.InteractionTimelineSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetInteractionTimelineListUseCase {

    private final InteractTimelineRepository interactTimelineRepository;
    private final InteractionTimelineMapper mapper;
    private final InteractionTimelineAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<InteractionTimelineResponse> execute(String search, String type, UUID agentId) {
        UserEntity currentUser = accessPolicy.currentUser();
        UUID scopedUserId = accessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        var spec = InteractionTimelineSpecification.filter(
                search,
                type,
                agentId,
                unscoped,
                scopedUserId
        );

        return interactTimelineRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "occurredAt"))
                .stream()
                .map(mapper::mapToResponse)
                .collect(Collectors.toList());
    }
}
