package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * What a conversion is still allowed to decide. Everything else comes from the lead.
 *
 * <p><b>Why this payload shrank.</b> It used to carry {@code fullName}, {@code email},
 * {@code phone}, {@code companyName} and {@code address}, and {@code ConvertLeadUseCase} built the
 * customer from those values without comparing a single one of them against the lead being
 * converted. UC-8.5 step 6 says the system "creates a customer profile <em>using the lead
 * information</em>", and the frontend did exactly that — it copied the lead's fields into the
 * request — but the server never required it. A direct API call could convert lead A into a
 * customer bearing an unrelated person's name and email, and {@code leads.customer_id} would then
 * point at a profile with nothing to do with the lead it claims to document. That silently defeats
 * BR-08: the lead snapshot is preserved for audit, and an audit trail whose two halves may disagree
 * records nothing.
 *
 * <p>Validation of those fields moved with them. It now happens once, on the lead, at create and
 * edit time (see {@code LeadFieldLimits}, {@code CreateLeadUseCase}, {@code UpdateLeadUseCase}) —
 * which is also why the length and pattern constraints that used to be duplicated here are gone
 * rather than merely relaxed. A value that reached the lead already passed them.
 *
 * <p>The two fields that remain are genuine decisions made at conversion time and have nowhere
 * else to come from.
 */
@Getter
@Setter
public class ConvertLeadRequest {

    /**
     * BR-09 — Individual or Corporate.
     *
     * <p>Optional: when omitted the lead's {@code isCorporate} flag decides, which is the normal
     * case. It stays settable because the individual/organization call is often only settled at the
     * moment of conversion, and the lead is still editable up to that point — but choosing
     * {@code CORPORATE} for a lead with no company name is refused rather than papered over
     * ({@code CustomerProfilePolicy.assertCorporateHasCompany}).
     */
    private CustomerType customerType;

    /**
     * The one customer field with no counterpart on the lead: a tax code is only ever needed once
     * an organization becomes a billable customer, so it is never asked for earlier.
     */
    @Size(max = LeadFieldLimits.TAX_CODE, message = "Tax code must be at most 25 characters")
    private String taxCode;

    /**
     * BR-07: a Sales Manager's approval reason for converting a lead that is not yet QUALIFIED
     * (e.g. a walk-in with a confirmed booking). Required only for that override path; ignored for
     * a normal QUALIFIED conversion.
     */
    @Size(max = 500, message = "Approval reason must be at most 500 characters")
    private String reason;
}
