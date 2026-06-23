package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSlaWarningUseCase {

    private final SlaTrackingRepository slaTrackingRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final BookingRepository bookingRepository;
    private final TaskRepository taskRepository;
    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final OpHandoverRepository opHandoverRepository;

    /**
     * UC-17.2 step 5-6: Find ACTIVE records whose warningAt has passed but warning
     * notification has not been sent yet. Notify assigned staff + all managers.
     *
     * @return number of warning notifications processed
     */
    @Transactional
    public int execute() {
        List<SlaTrackingEntity> pendingWarnings = slaTrackingRepository
                .findByStatusAndWarningAtBeforeAndWarningNotifiedFalse(SlaStatus.ACTIVE, OffsetDateTime.now());

        if (pendingWarnings.isEmpty()) return 0;

        List<UserEntity> managers = userRepository.findAllWithRole().stream()
                .filter(u -> {
                    String role = u.getRole().getRoleName();
                    return "MANAGER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
                })
                .toList();

        for (SlaTrackingEntity tracking : pendingWarnings) {
            tracking.setWarningNotified(true);
            slaTrackingRepository.save(tracking);

            Set<UUID> seen = new HashSet<>();
            List<UserEntity> recipients = new ArrayList<>();
            for (UserEntity m : managers) {
                if (seen.add(m.getUserId())) recipients.add(m);
            }
            UserEntity assigned = resolveAssignedUser(tracking);
            if (assigned != null && seen.add(assigned.getUserId())) recipients.add(assigned);

            String title   = "SLA Warning: " + activityLabel(tracking.getActivityType());
            String message = String.format(
                    "%s SLA deadline is approaching for %s. Take action now to avoid a breach.",
                    activityLabel(tracking.getActivityType()),
                    entityLabel(tracking.getEntityType()));

            for (UserEntity recipient : recipients) {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(recipient)
                        .title(title)
                        .message(message)
                        .type("SLA_WARNING")
                        .relatedEntity("SLA")
                        .relatedId(tracking.getTrackingId())
                        .build();
                notificationRepository.save(notification);
            }

            log.warn("SLA warning sent: trackingId={}, entityType={}, entityId={}, recipients={}",
                    tracking.getTrackingId(), tracking.getEntityType(),
                    tracking.getEntityId(), recipients.size());
        }

        return pendingWarnings.size();
    }

    private UserEntity resolveAssignedUser(SlaTrackingEntity tracking) {
        try {
            return switch (tracking.getEntityType()) {
                case "LEAD"      -> leadRepository.findById(tracking.getEntityId())
                                        .map(LeadEntity::getAssignedUser).orElse(null);
                case "BOOKING"   -> bookingRepository.findById(tracking.getEntityId())
                                        .map(BookingEntity::getAssignedUser).orElse(null);
                case "TASK"      -> taskRepository.findById(tracking.getEntityId())
                                        .map(TaskEntity::getAssignedUser).orElse(null);
                case "QUOTATION" -> quotationRepository.findById(tracking.getEntityId())
                                        .map(QuotationEntity::getCreatedBy).orElse(null);
                case "PAYMENT"   -> paymentRepository.findById(tracking.getEntityId())
                                        .map(p -> p.getBooking() != null ? p.getBooking().getAssignedUser() : null)
                                        .orElse(null);
                case "HANDOVER"  -> opHandoverRepository.findById(tracking.getEntityId())
                                        .map(h -> h.getBooking() != null ? h.getBooking().getAssignedUser() : null)
                                        .orElse(null);
                default          -> null;
            };
        } catch (Exception e) {
            log.warn("Could not resolve assigned user for {}/{}: {}",
                    tracking.getEntityType(), tracking.getEntityId(), e.getMessage());
            return null;
        }
    }

    private static String activityLabel(String activityType) {
        return switch (activityType) {
            case "LEAD_RESPONSE"              -> "Lead Response";
            case "QUOTATION_SENT"             -> "Quotation Dispatch";
            case "FOLLOW_UP_TASK"             -> "Follow-up Task";
            case "BOOKING_CONFIRM"            -> "Booking Confirmation";
            case "PAYMENT_DEPOSIT"            -> "Payment Deposit";
            case "HANDOVER_SUBMISSION"        -> "Handover Submission";
            case "QUOTATION_APPROVAL"         -> "Quotation Approval";
            case "CUSTOMER_FEEDBACK_RESPONSE" -> "Customer Feedback Response";
            default                           -> activityType;
        };
    }

    private static String entityLabel(String entityType) {
        return switch (entityType) {
            case "LEAD"      -> "Lead";
            case "QUOTATION" -> "Quotation";
            case "BOOKING"   -> "Booking";
            case "TASK"      -> "Task";
            default          -> entityType;
        };
    }
}