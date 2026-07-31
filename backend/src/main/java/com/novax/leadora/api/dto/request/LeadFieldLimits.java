package com.novax.leadora.api.dto.request;

/**
 * Field limits for the lead and customer request DTOs, kept in one place.
 *
 * <p><b>Why these exist as constants.</b> The three lead DTOs each declared their own
 * {@code @Size(max = …)}, and they drifted: create and update said 255 where the column is
 * {@code VARCHAR(40)}, convert said 20 for a phone stored in {@code VARCHAR(15)} and 50 for a tax
 * code stored in {@code VARCHAR(25)}. Every one of those gaps was a range of inputs that passed
 * validation and then failed at the database as an unexplained HTTP 500.
 *
 * <p>Three copies of a number that must agree will not stay in agreement. One named constant per
 * column means the next DTO added to this package inherits the right limit instead of guessing at
 * it — the same reasoning behind sharing the duplicate-detection policy rather than copying it.
 *
 * <p><b>These values track the schema, not a preference.</b> The project runs
 * {@code ddl-auto: validate} with no migration framework, so the columns are the fixed side of the
 * contract. If a column is ever widened, change it here in the same commit; a constant that is
 * looser than its column is exactly the bug this class was written to remove.
 */
public final class LeadFieldLimits {

    private LeadFieldLimits() {
    }

    // ── leads / customers: shared contact columns ────────────────────────────
    /** {@code leads.full_name} / {@code customers.full_name} — {@code VARCHAR(40) NOT NULL}. */
    public static final int FULL_NAME = 40;
    /** {@code leads.email} / {@code customers.email} — {@code VARCHAR(40)}. */
    public static final int EMAIL = 40;
    /** {@code leads.company_name} / {@code customers.company_name} — {@code VARCHAR(40)}. */
    public static final int COMPANY_NAME = 40;
    /** {@code leads.source} — {@code VARCHAR(40)}. */
    public static final int SOURCE = 40;
    /** {@code leads.interested_service} — {@code VARCHAR(100)}. */
    public static final int INTERESTED_SERVICE = 100;
    /** {@code customers.tax_code} — {@code VARCHAR(25)}. */
    public static final int TAX_CODE = 25;

    /**
     * A phone number: 10 or 11 digits, or empty to clear the field.
     *
     * <p><b>Why digits-only rather than the Vietnamese mobile shape.</b> This used to be
     * {@code ^(0[35789])\d{8}$} — exactly ten digits on a mobile prefix. That rejects a landline
     * ({@code 028…}), an eleven-digit number, and every number a guest gives from abroad, none of
     * which are wrong; they are simply not mobile numbers. A CRM whose leads arrive from walk-ins
     * and corporate switchboards cannot treat "not a VN mobile" as "not a phone number".
     *
     * <p>The trade-off is real and deliberate: {@code 1234567890} now passes. The pattern checks
     * the <em>shape</em> of the field; whether the number reaches anyone is something only a call
     * will establish, and no regex was ever going to.
     *
     * <p><b>One constant, six DTOs.</b> The lead DTOs referenced this while the customer, user and
     * profile DTOs each carried their own inline copy of the old expression. Six copies of a rule
     * that must agree do not stay in agreement — and here they could not even be allowed to
     * disagree: converting a lead copies its phone straight onto the new customer, so a number the
     * lead accepts but {@code UpdateCustomerRequest} rejects produces a customer that can never be
     * edited again.
     *
     * <p>The {@code ^$} branch is what makes a stored phone erasable. Without it the two contact
     * fields behaved differently for no reason a user could see: {@code @Email} treats "" as valid,
     * so an email could be cleared, while a blank phone failed the pattern with a 400 — and sending
     * {@code null} means "leave unchanged", so there was no way to remove a wrong number at all.
     *
     * <p>Allowing blank here does not weaken BR-05: that rule requires a phone <em>or</em> an
     * email, and it is enforced on the resulting record in {@code UpdateLeadUseCase} rather than
     * field by field, which is the only place that can see both at once.
     */
    public static final String PHONE_PATTERN = "^$|^\\d{10,11}$";

    /** The message every {@code @Pattern(regexp = PHONE_PATTERN)} shows, so they cannot drift. */
    public static final String PHONE_MESSAGE = "Phone number must be 10 or 11 digits";
}
