package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

/**
 * UC-8.5 steps 7-9 — everything that happens to a lead once its customer exists, whether that
 * customer was just created ({@link ConvertLeadUseCase}) or already did (E6,
 * {@link LinkLeadToCustomerUseCase}).
 *
 * <p>Three things happen here, and two of them were missing entirely before this class existed:
 *
 * <ol>
 *   <li><b>The lead becomes a CONVERTED snapshot</b>, linked to the customer in both directions.
 *       This part was already implemented.</li>
 *   <li><b>SLA tracking stops.</b> {@code UpdateLeadUseCase} auto-resolves the {@code LEAD_RESPONSE}
 *       SLA the moment a lead leaves {@code NEW}; conversion did not. The BR-07 override converts
 *       straight out of {@code NEW}, so its tracking row stayed {@code ACTIVE} and
 *       {@code SlaBreachScheduler} — which runs every 30 seconds — went on to raise breach warnings
 *       against a lead that had been a paying customer for days. Nobody can act on those: the lead
 *       is locked, and the thing the SLA was measuring already happened.</li>
 *   <li><b>The action is audited.</b> UC-8.5 step 9 asks for it and BR-08 depends on it, but no
 *       audit entry was written and {@code BaseEntity} carries no {@code updated_by} — so after a
 *       conversion the system genuinely could not say who had performed it. The infrastructure was
 *       already there and used by seven other use cases; only this one never called it.</li>
 * </ol>
 *
 * <p>Both extras are non-fatal on purpose. A conversion that succeeded must not be rolled back
 * because a log write failed — that would trade a missing audit row for a missing customer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadConversionCompleter {

    private final LeadRepository leadRepository;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final SystemAuditLogService systemAuditLogService;

    /**
     * @param overrideReason the BR-07 manager approval reason, or {@code null} for an ordinary
     *                       QUALIFIED conversion
     * @param action         audit action — {@code "CONVERTED"} or {@code "LINKED_TO_CUSTOMER"};
     *                       the two are distinguishable in the audit trail because only one of them
     *                       created a customer row
     * @return the saved lead
     */
    public LeadEntity complete(LeadEntity lead, CustomerEntity customer, UserEntity actor,
                               String overrideReason, String action) {

        LeadStatus previousStatus = lead.getStatus();

        // The manager's reason is also written onto the lead, not only into the audit log. The audit
        // table has no screen yet, and the lead is about to be locked — so `notes` is the only place
        // the next person to open this record will actually see why an unqualified lead became a
        // customer. Appended rather than assigned: it is still the user's field.
        if (StringUtils.hasText(overrideReason)) {
            String note = "[Converted with manager approval by "
                    + (actor != null ? actor.getFullName() : "unknown") + ": " + overrideReason + "]";
            lead.setNotes(StringUtils.hasText(lead.getNotes()) ? lead.getNotes() + "\n" + note : note);
        }

        // Step 7-8: BR-08 — the lead record is preserved, not deleted, and linked to its customer.
        // The other half of the link (customers.lead_id) is set by the caller that owns the
        // customer row.
        lead.setStatus(LeadStatus.CONVERTED);
        lead.setConvertedAt(OffsetDateTime.now());
        lead.setCustomer(customer);

        LeadEntity savedLead = leadRepository.save(lead);

        // The lead has been responded to in the most complete way possible — it became a customer.
        resolveSlaTracking(lead);

        // Step 9: log the conversion action.
        systemAuditLogService.log("LEAD", "LEAD", lead.getLeadId(), action, actor,
                previousStatus.name(), LeadStatus.CONVERTED.name(),
                auditNotes(customer, overrideReason));

        return savedLead;
    }

    private void resolveSlaTracking(LeadEntity lead) {
        try {
            resolveSlaBreachUseCase.executeByEntity("LEAD", lead.getLeadId());
        } catch (Exception e) {
            log.warn("SLA auto-resolve on conversion failed for lead {}: {}",
                    lead.getLeadId(), e.getMessage());
        }
    }

    private static String auditNotes(CustomerEntity customer, String overrideReason) {
        String notes = "customerId=" + (customer != null ? customer.getCustomerId() : null);
        return StringUtils.hasText(overrideReason)
                ? notes + ", managerOverride=" + overrideReason
                : notes;
    }
}
