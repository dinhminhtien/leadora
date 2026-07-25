package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves the staff member assigned to the entity an SlaTrackingEntity monitors —
 * shared between breach notification (ProcessSlaBreachUseCase) and ownership checks
 * (ResolveSlaBreachUseCase).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaEntityResolver {

    private final LeadRepository leadRepository;
    private final TaskRepository taskRepository;
    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final OpHandoverRepository opHandoverRepository;

    public UserEntity resolveAssignedUser(SlaTrackingEntity tracking) {
        try {
            return switch (tracking.getEntityType()) {
                case "LEAD" -> leadRepository.findById(tracking.getEntityId())
                        .map(l -> l.getAssignedUser()).orElse(null);
                case "TASK" -> taskRepository.findById(tracking.getEntityId())
                        .map(t -> t.getAssignedUser()).orElse(null);
                case "QUOTATION" -> quotationRepository.findById(tracking.getEntityId())
                        .map(q -> q.getCreatedBy()).orElse(null);
                case "PAYMENT" -> paymentRepository.findById(tracking.getEntityId())
                        .map(p -> p.getBooking() != null ? p.getBooking().getAssignedUser() : null)
                        .orElse(null);
                case "HANDOVER" -> opHandoverRepository.findById(tracking.getEntityId())
                        .map(h -> h.getBooking() != null ? h.getBooking().getAssignedUser() : null)
                        .orElse(null);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Could not resolve assigned user for {}/{}: {}",
                    tracking.getEntityType(), tracking.getEntityId(), e.getMessage());
            return null;
        }
    }
}
