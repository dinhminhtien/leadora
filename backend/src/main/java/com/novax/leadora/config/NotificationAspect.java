package com.novax.leadora.config;

import com.novax.leadora.application.usecase.notification.NotificationCreatedEvent;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class NotificationAspect {

    private final ApplicationEventPublisher eventPublisher;

    @AfterReturning(
            pointcut = "execution(* com.novax.leadora.infrastructure.persistence.repository.NotificationRepository.save(..))",
            returning = "result"
    )
    public void afterSave(Object result) {
        if (result instanceof NotificationEntity) {
            eventPublisher.publishEvent(new NotificationCreatedEvent(this, (NotificationEntity) result));
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.novax.leadora.infrastructure.persistence.repository.NotificationRepository.saveAll(..))",
            returning = "result"
    )
    public void afterSaveAll(Object result) {
        if (result instanceof Iterable) {
            for (Object item : (Iterable<?>) result) {
                if (item instanceof NotificationEntity) {
                    eventPublisher.publishEvent(new NotificationCreatedEvent(this, (NotificationEntity) item));
                }
            }
        }
    }
}
