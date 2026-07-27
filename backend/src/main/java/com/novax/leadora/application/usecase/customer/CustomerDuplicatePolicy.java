package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * The single place that decides whether a customer's contact details collide with an existing one.
 *
 * <p><b>Why this is a component and not a private method.</b> There are three ways a row reaches
 * the {@code customers} table — create, update, and converting a lead — and only two of them were
 * guarded. {@code ConvertLeadUseCase} called {@code customerRepository.save()} directly, so it
 * skipped the duplicate logic sitting in the class next door and quietly produced a second customer
 * for the same person. There is no unique index on {@code customers.email}/{@code phone} either, so
 * nothing downstream caught it: the split only surfaced later as one person's history appearing
 * under two records and every count being one too high.
 *
 * <p>Copying the check into the third caller would have fixed today's bug and left the fourth
 * caller to reintroduce it. Sharing the rule is what actually closes the class of problem.
 *
 * <p>Callers that already own a record pass its id to {@code excludeId} so a save that leaves the
 * email untouched does not report the record as a duplicate of itself.
 */
@Component
@RequiredArgsConstructor
public class CustomerDuplicatePolicy {

    private final CustomerRepository customerRepository;

    /** Rejects a new customer whose email or phone already belongs to someone else. */
    public void assertNoDuplicate(String email, String phone) {
        assertNoDuplicate(email, phone, null);
    }

    /**
     * @param excludeId the customer being edited, so it is not compared against itself; {@code null}
     *                  when the record does not exist yet
     */
    public void assertNoDuplicate(String email, String phone, UUID excludeId) {
        // Email first: it is the more reliable identity, so it gives the more useful message when
        // both happen to collide.
        if (StringUtils.hasText(email)) {
            findOther(customerRepository.findFirstByEmail(email.trim()), excludeId)
                    .ifPresent(existing -> {
                        throw new BusinessException(
                                "DUPLICATE_CUSTOMER_EMAIL",
                                "A customer with email '" + email.trim() + "' already exists.",
                                idOf(existing),
                                HttpStatus.CONFLICT);
                    });
        }
        if (StringUtils.hasText(phone)) {
            findOther(customerRepository.findFirstByPhone(phone.trim()), excludeId)
                    .ifPresent(existing -> {
                        throw new BusinessException(
                                "DUPLICATE_CUSTOMER_PHONE",
                                "A customer with phone '" + phone.trim() + "' already exists.",
                                idOf(existing),
                                HttpStatus.CONFLICT);
                    });
        }
    }

    /**
     * The matching customer's id for the UI to link to, or {@code null} if it has none yet.
     *
     * <p>Null-safe on purpose: an entity that has not been flushed carries no id, and losing the
     * duplicate message to a {@code NullPointerException} would replace a clear 409 with an opaque
     * 500 — turning a helpful refusal into the exact kind of error this work set out to remove.
     */
    private static String idOf(CustomerEntity customer) {
        return customer.getCustomerId() != null ? customer.getCustomerId().toString() : null;
    }

    /** Drops a match that is the record being edited — a row is never its own duplicate. */
    private static Optional<CustomerEntity> findOther(Optional<CustomerEntity> match, UUID excludeId) {
        return match.filter(c -> excludeId == null || !excludeId.equals(c.getCustomerId()));
    }
}
