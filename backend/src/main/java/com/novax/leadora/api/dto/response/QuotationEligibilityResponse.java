package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.application.usecase.quotation.QuotationActionPolicy;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * What this quotation currently allows, and the reason for anything it does not.
 *
 * <p>Exists so the client can render a disabled button with the true explanation instead of
 * discovering the refusal by attempting the action and surfacing an error. The verdicts come
 * from {@link QuotationActionPolicy} — the same object the write paths call — so the reason
 * shown in advance is word for word the reason an attempt would have produced.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationEligibilityResponse {

    private UUID quotationId;
    private String status;

    /** Status and customer identity — what every delivery method needs. */
    private ActionEligibility send;
    /** Sending by email: {@link #send} plus a deliverable address. */
    private ActionEligibility sendByEmail;
    /** Sending by WhatsApp/SMS: {@link #send} plus a phone number. */
    private ActionEligibility sendByWhatsApp;
    /**
     * Creating the booking.
     *
     * <p>There is no {@code requestAvailability} entry: asking the Reservation team is not an
     * action a user takes any more. The request is raised by the workflow when the customer
     * accepts, so there is no button to enable or disable.
     */
    private ActionEligibility convert;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActionEligibility {
        private boolean allowed;
        /** Machine-readable reason, matching the code the write path would return. */
        private String errorCode;
        /** The sentence to show the user. Null when allowed. */
        private String reason;
        /** Dotted path of the input to fix, when there is one. */
        private String field;

        static ActionEligibility from(QuotationActionPolicy.Verdict verdict) {
            return ActionEligibility.builder()
                    .allowed(verdict.allowed())
                    .errorCode(verdict.errorCode())
                    .reason(verdict.message())
                    .field(verdict.field())
                    .build();
        }
    }

    public static QuotationEligibilityResponse of(
            UUID quotationId,
            String status,
            QuotationActionPolicy.Verdict send,
            QuotationActionPolicy.Verdict sendByEmail,
            QuotationActionPolicy.Verdict sendByWhatsApp,
            QuotationActionPolicy.Verdict convert) {
        return QuotationEligibilityResponse.builder()
                .quotationId(quotationId)
                .status(status)
                .send(ActionEligibility.from(send))
                .sendByEmail(ActionEligibility.from(sendByEmail))
                .sendByWhatsApp(ActionEligibility.from(sendByWhatsApp))
                .convert(ActionEligibility.from(convert))
                .build();
    }
}
