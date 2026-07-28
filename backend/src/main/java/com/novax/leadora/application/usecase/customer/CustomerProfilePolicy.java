package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The shape a {@code customers} row must have, independent of which door it came through.
 *
 * <p><b>Why this exists.</b> {@link CustomerDuplicatePolicy} was written because the duplicate rule
 * had been copied into two of the three write paths and forgotten in the third. BR-09 then repeated
 * the same story one class over: the "a corporate customer must name its company" check was written
 * out by hand in {@code CreateCustomerUseCase} and again in {@code UpdateCustomerUseCase}, and
 * {@code ConvertLeadUseCase} — the third way a customer is created — had neither. A lead converted
 * as CORPORATE could therefore produce a corporate profile with no company name, which the very next
 * edit through {@code UpdateCustomerUseCase} would refuse to save. The record was already invalid by
 * the rules the system enforces everywhere else.
 *
 * <p>Two rules live here rather than one because they answer the same question — "is this profile
 * usable?" — and because splitting them across classes is what produced the drift in the first
 * place.
 */
@Component
public class CustomerProfilePolicy {

    /**
     * BR-09 — a corporate profile must name its company.
     *
     * <p>Kept at {@code 400 BAD_REQUEST}: that is what the two existing callers already returned,
     * and the frontend and {@code CreateCustomerUseCaseTest} were written against it. Sharing a
     * rule is not a reason to silently change the status code three screens depend on.
     */
    public void assertCorporateHasCompany(CustomerType customerType, String companyName) {
        if (customerType == CustomerType.CORPORATE && !StringUtils.hasText(companyName)) {
            throw new BusinessException(
                    "CUSTOMER_COMPANY_REQUIRED",
                    "Company name is required for a corporate customer.",
                    "companyName",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * A customer profile must carry at least one way to reach the person.
     *
     * <p><b>Only the conversion path calls this, deliberately.</b> BR-05 already forces a phone or
     * an email onto any lead in active follow-up, so a lead converted from {@code QUALIFIED} always
     * satisfies it. The BR-07 manager override does not go through {@code QUALIFIED} — a lead can
     * be converted straight out of {@code NEW}, where BR-05 is explicitly waived so a walk-in can be
     * recorded in seconds — and that is the gap: the exemption is meant to defer the details, not to
     * mint a customer nobody can contact, whose quotations and bookings have nowhere to be sent.
     *
     * <p>It is not applied to {@code CreateCustomerUseCase}/{@code UpdateCustomerUseCase} because
     * requiring it there would change the contract of UC-9.x, which is a separate decision from
     * this one and not ours to make here.
     */
    public void assertReachable(String email, String phone) {
        if (!StringUtils.hasText(email) && !StringUtils.hasText(phone)) {
            throw new BusinessException(
                    "CUSTOMER_NOT_REACHABLE",
                    "A customer profile needs a phone number or an email address. "
                            + "Add one to the lead, then convert it.",
                    "phoneOrEmail",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

}
