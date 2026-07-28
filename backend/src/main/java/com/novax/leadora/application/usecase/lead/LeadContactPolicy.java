package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.application.usecase.customer.CustomerDuplicatePolicy;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.DuplicateLeadException;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * The contact rules a lead must satisfy, on every path that writes one.
 *
 * <p><b>Why one class.</b> Creating and editing a lead had drifted into enforcing different rules:
 * create checked the contact fields against other leads, edit checked them too but only after being
 * fixed once already, and <em>neither</em> checked them against {@code customers} until the create
 * path was recently taught to. Reachability was looser still — nothing required a phone or an email
 * at all until the lead tried to leave {@code NEW}, so a lead could sit in the pipeline for days as
 * a name with no way to contact it.
 *
 * <p>Both rules now live here and both write paths call the same method, which is the only
 * arrangement where "create and edit agree" is true by construction rather than by review.
 */
@Component
@RequiredArgsConstructor
public class LeadContactPolicy {

    private final LeadRepository leadRepository;
    private final CustomerDuplicatePolicy customerDuplicatePolicy;

    /**
     * A lead needs at least one way to reach the person.
     *
     * <p>Applied at create and edit rather than only at the {@code CONTACTED}/{@code QUALIFIED}
     * transition, where BR-05 used to be the sole gate. A record of an enquiry with neither a phone
     * nor an email cannot be followed up, quoted, or converted — the earlier rule let one be stored
     * and then blocked every subsequent action on it, which reports the problem at the point where
     * it is most expensive to fix.
     */
    public void assertReachable(String email, String phone) {
        if (!StringUtils.hasText(email) && !StringUtils.hasText(phone)) {
            throw new BusinessException(
                    "LEAD_NOT_REACHABLE",
                    "A lead needs a phone number or an email address so it can be followed up.",
                    "phoneOrEmail",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Refuses contact details that already belong to another lead or to a customer.
     *
     * <p>Customers are checked first: a match there means the person is already on the books, and
     * sending the user to that profile is what tells them what to do next. A lead match usually
     * means the twin was already converted into exactly that customer, so it is the less useful of
     * the two answers when both are true.
     *
     * <p>Blank values are skipped — they are not an identity, and {@code assertReachable} has
     * already refused the case where <em>both</em> are blank.
     *
     * @param excludeLeadId the lead being edited, so it is never reported as a duplicate of itself;
     *                      {@code null} when creating
     */
    public void assertNoDuplicate(String email, String phone, UUID excludeLeadId) {
        customerDuplicatePolicy.assertNoDuplicate(email, phone);

        if (StringUtils.hasText(email)) {
            String value = email.trim();
            leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(value)
                    .filter(other -> excludeLeadId == null || !excludeLeadId.equals(other.getLeadId()))
                    .ifPresent(other -> {
                        throw new DuplicateLeadException("email", value, other.getLeadId());
                    });
        }
        if (StringUtils.hasText(phone)) {
            String value = phone.trim();
            leadRepository.findFirstByPhoneOrderByCreatedAtDesc(value)
                    .filter(other -> excludeLeadId == null || !excludeLeadId.equals(other.getLeadId()))
                    .ifPresent(other -> {
                        throw new DuplicateLeadException("phone number", value, other.getLeadId());
                    });
        }
    }
}
