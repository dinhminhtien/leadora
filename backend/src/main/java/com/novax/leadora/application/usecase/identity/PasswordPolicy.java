package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The one place the account password rule lives.
 *
 * <p><b>The rule, from SRS 3.3.2:</b> "Min 6 chars, uppercase, lowercase, digit, symbol" - the
 * wording the Create User form shows in its own placeholder. Six characters, and all four
 * character classes.
 *
 * <p><b>Why this class exists.</b> The same check was written out four times, once per use case
 * (create, update, reset, change), and the copies had already drifted apart in two ways that a
 * reader of any single copy could not see:
 * <ul>
 *   <li>{@code ChangePasswordUseCase} checked the four character classes but <b>no length at
 *       all</b>, so {@code Ab1!} - four characters - was an acceptable new password there while
 *       being refused everywhere else.</li>
 *   <li>Three copies threw {@code IllegalStateException} (which the client can only render as a
 *       generic banner) while the fourth threw a typed {@code BusinessException} the frontend
 *       already knows how to present. Same rule, two different experiences.</li>
 * </ul>
 * Deduplicating is therefore not tidying: it is how the rule stops meaning four different things.
 *
 * <p>Failures carry {@code field = "password"} so the client can point at the offending input
 * rather than showing a banner and leaving the user to guess which box is wrong.
 */
public final class PasswordPolicy {

    /**
     * Minimum length, per the SRS.
     *
     * <p>Deliberately 6 and not 8. {@code CreateUserRequest} used to declare {@code @Size(min = 8)},
     * which runs as bean validation - i.e. <em>before</em> the use case - so a password that
     * satisfied the specified rule in full ({@code Ab1!cd}) was rejected with a 400 that no
     * business code had asked for, and the form's own placeholder promised six.
     */
    public static final int MIN_LENGTH = 6;

    /** Kept verbatim from the copies this replaces, so nothing asserting on them has to change. */
    static final String TOO_SHORT_MESSAGE = "Password must be at least " + MIN_LENGTH + " characters.";

    static final String WEAK_MESSAGE =
            "Password must contain at least one lowercase letter, one uppercase letter, "
                    + "one digit, and one symbol.";

    private PasswordPolicy() {
    }

    /**
     * @throws BusinessException 422, {@code PASSWORD_TOO_SHORT} or {@code WEAK_PASSWORD}
     */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw refusal("PASSWORD_TOO_SHORT", TOO_SHORT_MESSAGE);
        }

        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        // Whitespace is excluded on purpose: a space is neither a letter nor a digit, so counting
        // it as the symbol would let "Ab 1cd" pass a rule the user reads as requiring punctuation.
        boolean hasSymbol = password.chars()
                .anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));

        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSymbol) {
            throw refusal("WEAK_PASSWORD", WEAK_MESSAGE);
        }
    }

    private static BusinessException refusal(String code, String message) {
        return new BusinessException(code, message, null, HttpStatus.UNPROCESSABLE_ENTITY, "password");
    }
}
