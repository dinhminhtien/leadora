package com.novax.leadora.application.usecase.email;

import com.novax.leadora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * The one place that decides whether an address is good enough to hand to the email provider.
 *
 * <p>Every outbound email in the system routes its recipient through here, so a missing or
 * malformed address always produces the same refusal with the same code, whichever flow raised
 * it. Before this existed each sender made its own choice: the quotation email logged a warning
 * and returned as if it had sent, the OTP emails did the same — which is worse, because the
 * caller then told the customer to check their inbox for a code that was never sent — and
 * anything that did reach the provider failed a {@code @Valid} check inside the gateway and came
 * back to the user as HTTP 500.
 *
 * <p><b>Nothing here invents an address.</b> A missing address is reported, never substituted:
 * a placeholder would send a customer's quotation to a mailbox nobody reads and record it as
 * delivered.
 */
public final class EmailContactPolicy {

    /**
     * Deliberately close to the HTML5 {@code type=email} rule and to the front end's own check,
     * so a value the form accepted is not then rejected by the server for a different reason.
     * The provider remains the real authority on deliverability — this only rules out addresses
     * that cannot be delivered to under any circumstances.
     */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private EmailContactPolicy() {
    }

    public static boolean isValidEmail(String candidate) {
        return candidate != null && EMAIL.matcher(candidate.trim()).matches();
    }

    /**
     * The address to send to, or a refusal naming the field the user has to fix.
     *
     * @param candidate the address as resolved from the request or the customer record
     * @param field     dotted path the client can point at, e.g. {@code customer.email}
     * @param subject   what was being sent, for the message: "this quotation", "the contract"
     */
    public static String requireDeliverableEmail(String candidate, String field, String subject) {
        if (candidate == null || candidate.isBlank()) {
            throw BusinessException.forField("INVALID_CONTACT_INFORMATION",
                    "An email address is required to send " + subject
                            + ", and none is recorded for this customer. Add the customer's email address"
                            + " (or enter one for this send), then try again.",
                    HttpStatus.UNPROCESSABLE_ENTITY, field);
        }
        String trimmed = candidate.trim();
        if (!EMAIL.matcher(trimmed).matches()) {
            throw BusinessException.forField("INVALID_EMAIL_FORMAT",
                    "\"" + trimmed + "\" is not a valid email address, so " + subject
                            + " cannot be sent. Correct the address and try again.",
                    HttpStatus.UNPROCESSABLE_ENTITY, field);
        }
        return trimmed;
    }

    /** Same contract as {@link #requireDeliverableEmail}, for the phone-based delivery methods. */
    public static String requireContactPhone(String candidate, String field, String subject) {
        if (candidate == null || candidate.isBlank()) {
            throw BusinessException.forField("INVALID_CONTACT_INFORMATION",
                    "A phone number is required to send " + subject
                            + " by this method, and none is recorded for this customer. Add the customer's"
                            + " phone number (or enter one for this send), then try again.",
                    HttpStatus.UNPROCESSABLE_ENTITY, field);
        }
        return candidate.trim();
    }

    /**
     * No contact at all — used where the recipient is resolved from a record rather than typed,
     * so there is no field for the user to correct in place.
     */
    public static BusinessException recipientNotFound(String subject, String nextStep) {
        return new BusinessException("RECIPIENT_NOT_FOUND",
                "There is no recipient recorded for " + subject + ". " + nextStep,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
