package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * UC-8.5 step 4 and exception E4 — whether a lead may leave the pipeline as a customer at all.
 *
 * <p>Extracted because there are now two ways out: creating a new customer
 * ({@link ConvertLeadUseCase}) and attaching the lead to one that already exists
 * ({@link LinkLeadToCustomerUseCase}, exception E6). Both are conversions as far as the lead is
 * concerned — the lead ends up CONVERTED and locked either way — so both have to ask exactly the
 * same eligibility question. Answering it in two places is how the two answers stop matching.
 */
@Component
@RequiredArgsConstructor
public class LeadConversionPolicy {

    private final LeadAccessPolicy leadAccessPolicy;

    /**
     * Refuses a lead that must not be converted, and returns the manager's approval reason when the
     * BR-07 override was used ({@code null} for an ordinary QUALIFIED conversion).
     *
     * <p>Ordered so the most specific refusal wins: a lead that is already converted gets told that
     * rather than being told it is not qualified.
     */
    public String assertEligible(LeadEntity lead, UserEntity currentUser, String reason) {

        // Idempotency — already converted. 409 rather than 422: the request is well-formed, it has
        // simply lost a race (or is a double-click).
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new BusinessException("LEAD_ALREADY_CONVERTED",
                    "This lead has already been converted to a customer.",
                    HttpStatus.CONFLICT);
        }

        // LOST is terminal. UpdateLeadUseCase already refuses to move a lead out of it, and
        // conversion is the same kind of transition by another route. Without this the two files
        // disagreed: a Manager supplying a reason could convert a lead the rest of the system
        // treats as closed. Reopening is a deliberate act and should be one.
        if (lead.getStatus() == LeadStatus.LOST) {
            throw new BusinessException("LEAD_LOST",
                    "A lost lead cannot be converted. Reopen it first if the customer came back.",
                    "status",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // A lead must be assigned to a sales rep (by a Manager) before conversion. Unassigned leads
        // are drafts that never progress past NEW, and the customer inherits the lead's owner —
        // converting an unassigned one would produce an ownerless customer.
        if (lead.getAssignedUser() == null) {
            throw new BusinessException("LEAD_UNASSIGNED",
                    "Lead must be assigned to a sales rep before it can be converted.",
                    "assignedUserId",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // BR-07: QUALIFIED converts freely. Anything else needs a Sales Manager to approve with a
        // documented reason — which is also how the rule's "a confirmed booking / customer request
        // exists" branch is served, since no automated check for that exists yet.
        if (lead.getStatus() == LeadStatus.QUALIFIED) {
            return null;
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("LEAD_NOT_QUALIFIED",
                    "Lead must be QUALIFIED before conversion, or a Sales Manager must approve "
                            + "with a reason. Current status: " + lead.getStatus(),
                    "reason",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // Only a Manager/Admin may approve the exception. Checked after the reason so a Sales Staff
        // who simply forgot the field is told that, rather than being told they lack a permission
        // they would still not have with the field filled in.
        leadAccessPolicy.assertFullAccess(currentUser);
        return reason.trim();
    }
}
