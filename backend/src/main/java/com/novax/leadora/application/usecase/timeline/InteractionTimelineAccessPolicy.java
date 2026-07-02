package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InteractionTimelineAccessPolicy extends BaseAccessPolicy<InteractTimelineEntity> {

    public InteractionTimelineAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    protected boolean owns(UserEntity user, InteractTimelineEntity timeline) {
        UUID uid = user.getUserId();
        return timeline.getUser() != null && uid.equals(timeline.getUser().getUserId());
    }
}
