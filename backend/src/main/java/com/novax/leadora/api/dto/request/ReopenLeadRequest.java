package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * UC-8.4 — reopening a lead that was closed as LOST.
 *
 * <p>{@code LOST} is terminal everywhere else in the module: {@code UpdateLeadUseCase} refuses
 * every transition out of it, and {@code LeadConversionPolicy} refuses to convert one — telling the
 * user to "reopen it first", which until now named a route that did not exist. The result was a
 * genuine dead end whenever a guest who had gone quiet came back: the lead could not move, and a
 * replacement could not be created either because {@code LeadContactPolicy} rejects the duplicate
 * email/phone.
 *
 * <p>The reason is <b>required</b>, unlike the BR-07 conversion override where it is only needed
 * for the non-QUALIFIED path. Reopening rewrites a closed outcome — the number the Lost tile on the
 * list is counting — so the record has to say who decided that and why. It is written to the audit
 * trail and appended to the lead's notes, following {@code LeadConversionCompleter}: the audit
 * table has no screen yet, so notes is the only place the next person to open the lead will see it.
 */
@Getter
@Setter
public class ReopenLeadRequest {

    @NotBlank(message = "A reason is required to reopen a lost lead")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
