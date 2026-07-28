package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * UC-8.5 exception E6 — the lead describes someone who is already a customer, so it is attached to
 * that profile instead of minting a second one.
 *
 * <p>The id is all that is needed: the customer already exists and is not edited by this call. That
 * is deliberate — a "link" that also rewrote the existing profile from the lead would silently
 * overwrite whatever the customer's record has learned since, which is the opposite of what the
 * user asked for when they chose to link rather than create.
 *
 * <p>The {@code reason} field mirrors {@link ConvertLeadRequest}: a lead that is not QUALIFIED needs
 * the same BR-07 manager approval to leave the pipeline this way as it does any other way.
 */
@Getter
@Setter
public class LinkLeadToCustomerRequest {

    @NotNull(message = "Customer id is required")
    private UUID customerId;

    /** BR-07 — Sales Manager approval reason when linking a non-QUALIFIED lead. */
    @Size(max = 500, message = "Approval reason must be at most 500 characters")
    private String reason;
}
