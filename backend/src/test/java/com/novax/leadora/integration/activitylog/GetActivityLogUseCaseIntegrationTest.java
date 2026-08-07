package com.novax.leadora.integration.activitylog;

import com.novax.leadora.application.usecase.activitylog.GetActivityLogUseCase;
import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class GetActivityLogUseCaseIntegrationTest {

    @Autowired
    private GetActivityLogUseCase getActivityLogUseCase;

    @Test
    void testEffectiveViewQuery() {
        GetActivityLogUseCase.FilterQuery query = GetActivityLogUseCase.FilterQuery.builder()
                .view("EFFECTIVE")
                .category("BUSINESS")
                .build();
        Pageable pageable = PageRequest.of(0, 15);
        try {
            Page<ActivityLogEntity> result = getActivityLogUseCase.execute(query, pageable);
            System.out.println("SUCCESSFULLY EXECUTED: " + result.getTotalElements());

            // Map like controller to verify mapping outside transactional context
            Page<String> mapped = result.map(entity -> {
                if (entity.getActorType() == com.novax.leadora.infrastructure.persistence.entity.enums.ActorType.USER
                        && entity.getActorUser() != null) {
                    return entity.getActorUser().getFullName();
                }
                return "SYSTEM";
            });
            System.out.println("Mapped content size: " + mapped.getContent().size());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
