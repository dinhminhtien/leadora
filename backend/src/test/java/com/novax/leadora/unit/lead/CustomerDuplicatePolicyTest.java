package com.novax.leadora.unit.lead;

import com.novax.leadora.application.usecase.customer.CustomerDuplicatePolicy;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The rule that used to exist in two of the three places it was needed.
 *
 * <p>Converting a lead wrote to {@code customers} through the repository directly, skipping the
 * duplicate check that create and update both performed — and there is no unique index behind it,
 * so a second customer for the same person was created in silence. These tests pin the rule itself,
 * now that all three callers share it.
 */
class CustomerDuplicatePolicyTest {

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerDuplicatePolicy policy = new CustomerDuplicatePolicy(repository);

    private static CustomerEntity existing() {
        return CustomerEntity.builder().customerId(UUID.randomUUID()).build();
    }

    private void noMatches() {
        when(repository.findFirstByEmail(anyString())).thenReturn(Optional.empty());
        when(repository.findFirstByPhone(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("an unused email and phone pass")
    void allowsFreshContactDetails() {
        noMatches();

        policy.assertNoDuplicate("new@hotel.vn", "0912345678");
    }

    @Test
    @DisplayName("an email already held by a customer is refused, carrying that customer's id")
    void refusesDuplicateEmail() {
        noMatches();
        CustomerEntity other = existing();
        when(repository.findFirstByEmail("taken@hotel.vn")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> policy.assertNoDuplicate("taken@hotel.vn", "0912345678"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_CUSTOMER_EMAIL");
                    // The id is what lets the UI offer "open the existing customer".
                    assertThat(ex.getDetails()).isEqualTo(other.getCustomerId().toString());
                });
    }

    @Test
    @DisplayName("a taken phone is refused too")
    void refusesDuplicatePhone() {
        noMatches();
        when(repository.findFirstByPhone("0912345678")).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> policy.assertNoDuplicate(null, "0912345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("DUPLICATE_CUSTOMER_PHONE");
    }

    @Test
    @DisplayName("email is reported first when both collide — the more reliable identity")
    void emailTakesPrecedenceOverPhone() {
        when(repository.findFirstByEmail(anyString())).thenReturn(Optional.of(existing()));
        when(repository.findFirstByPhone(anyString())).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> policy.assertNoDuplicate("taken@hotel.vn", "0912345678"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("DUPLICATE_CUSTOMER_EMAIL");
    }

    @Test
    @DisplayName("a record is never its own duplicate")
    void ignoresAMatchOnTheRecordBeingEdited() {
        noMatches();
        CustomerEntity self = existing();
        when(repository.findFirstByEmail("mine@hotel.vn")).thenReturn(Optional.of(self));

        policy.assertNoDuplicate("mine@hotel.vn", null, self.getCustomerId());
    }

    @Test
    @DisplayName("editing onto someone else's email is still refused")
    void stillRefusesAnotherRecordsEmailWhileEditing() {
        noMatches();
        when(repository.findFirstByEmail("theirs@hotel.vn")).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() ->
                policy.assertNoDuplicate("theirs@hotel.vn", null, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("blank contact details claim no identity and are skipped")
    void skipsBlankValues() {
        policy.assertNoDuplicate("", "   ");
        policy.assertNoDuplicate(null, null);
    }

    @Test
    @DisplayName("surrounding whitespace does not smuggle a duplicate past the check")
    void trimsBeforeLookup() {
        noMatches();
        when(repository.findFirstByEmail("taken@hotel.vn")).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> policy.assertNoDuplicate("  taken@hotel.vn  ", null))
                .isInstanceOf(BusinessException.class);
    }
}
