package com.novax.leadora.common.util;

import org.springframework.util.StringUtils;

/**
 * Small text helpers shared across modules.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * Turns a blank string into {@code null}, trimming whatever is left.
     *
     * <p><b>Why every optional text column must go through this.</b> The database enforces contact
     * uniqueness with partial indexes — {@code uq_leads_email_lower ... WHERE email IS NOT NULL},
     * and the same pair on {@code customers}. Those exclude {@code NULL} but <em>not</em> the empty
     * string: {@code '' IS NOT NULL} is true, so every record saved with {@code ""} instead of
     * {@code NULL} takes a slot in the index. The first lead with no email claims it; the second
     * one collides and the insert fails with a constraint violation that mentions a "duplicate"
     * the user never entered, because the form field they left blank was never a value at all.
     *
     * <p>It is not only about the index. {@code ""} makes every "has an email?" query in reporting
     * answer yes, and the service-layer duplicate checks skip blanks — so a stored {@code ""} is
     * invisible to the check that is supposed to guard the column and visible only to the database
     * that rejects it.
     */
    public static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
