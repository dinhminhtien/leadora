package com.novax.leadora.unit.activitylog;

import com.novax.leadora.application.event.BusinessActivityEvent;
import com.novax.leadora.application.event.SecurityAuditEvent;
import com.novax.leadora.application.listener.ActivityLogListener;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.AppendActivityLogUseCase;
import com.novax.leadora.infrastructure.activitylog.SpringActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityLogListenerTest {

    @Mock
    private AppendActivityLogUseCase appendActivityLogUseCase;

    @InjectMocks
    private ActivityLogListener activityLogListener;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SpringActivityLogPublisher springActivityLogPublisher;

    @Test
    @DisplayName("should call append usecase on handleBusinessActivity")
    void shouldCallAppendUseCaseOnHandleBusinessActivity() {
        ActivityLogCommand command = ActivityLogCommand.builder()
                .activityType(ActivityLogType.LEAD_CREATED)
                .summary("Lead created")
                .build();
        BusinessActivityEvent event = new BusinessActivityEvent(command);

        activityLogListener.handleBusinessActivity(event);

        verify(appendActivityLogUseCase, times(1)).execute(command);
    }

    @Test
    @DisplayName("should call append usecase on handleSecurityActivity")
    void shouldCallAppendUseCaseOnHandleSecurityActivity() {
        ActivityLogCommand command = ActivityLogCommand.builder()
                .activityType(ActivityLogType.LOGIN_FAILED)
                .summary("Failed login")
                .build();
        SecurityAuditEvent event = new SecurityAuditEvent(command);

        activityLogListener.handleSecurityActivity(event);

        verify(appendActivityLogUseCase, times(1)).execute(command);
    }

    @Test
    @DisplayName("should publish SecurityAuditEvent for security log type")
    void shouldPublishSecurityAuditEventForSecurityLogType() {
        ActivityLogCommand command = ActivityLogCommand.builder()
                .activityType(ActivityLogType.LOGIN_FAILED)
                .summary("Failed login")
                .build();

        springActivityLogPublisher.publish(command);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        Object publishedEvent = captor.getValue();
        assertThat(publishedEvent).isInstanceOf(SecurityAuditEvent.class);
        SecurityAuditEvent securityEvent = (SecurityAuditEvent) publishedEvent;
        assertThat(securityEvent.command()).isEqualTo(command);
    }

    @Test
    @DisplayName("should publish BusinessActivityEvent for business log type")
    void shouldPublishBusinessActivityEventForBusinessLogType() {
        ActivityLogCommand command = ActivityLogCommand.builder()
                .activityType(ActivityLogType.LEAD_CREATED)
                .summary("Lead created")
                .build();

        springActivityLogPublisher.publish(command);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        Object publishedEvent = captor.getValue();
        assertThat(publishedEvent).isInstanceOf(BusinessActivityEvent.class);
        BusinessActivityEvent businessEvent = (BusinessActivityEvent) publishedEvent;
        assertThat(businessEvent.command()).isEqualTo(command);
    }
}
