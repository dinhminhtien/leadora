package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.common.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/**
 * Request-parameter parsing for the handover list endpoints.
 *
 * <p>Replaces two habits that were both wrong in the same place:
 *
 * <ul>
 *   <li><b>Unvalidated sort/paging.</b> {@code Sort.by(direction, sortBy)} took the raw string, so
 *       {@code ?sortBy=nope} reached Spring Data, threw {@code PropertyReferenceException}, fell
 *       through to the catch-all handler and came back as <b>HTTP 500</b> — a client mistake
 *       reported as a server fault. {@code size=0} and {@code page=-1} did the same via
 *       {@code PageRequest.of}. Sorting is now a whitelist, so an unknown field is a 400 and an
 *       unmapped entity path is impossible.</li>
 *   <li><b>Silently swallowed filters.</b> A bad readiness value or malformed date was caught and
 *       turned into {@code null}, i.e. <em>no filter at all</em>, so the endpoint answered with the
 *       whole table. Asking for {@code ?readinessStatus=READY} (a value that no longer exists)
 *       returned every arrival and looked like they were all READY. Failing loudly is the only safe
 *       option: a filter that quietly does nothing is worse than an error.</li>
 * </ul>
 *
 * <p>The sort whitelist also keeps entity paths out of the API: callers name
 * {@code arrivalDate}, the mapping decides that means {@code booking.checkInDate}.
 */
final class HandoverListQuery {

    /** Hard ceiling on page size — an unbounded {@code size} is a denial-of-service parameter. */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Sortable columns for both handover lists, API name → entity path.
     *
     * <p>Shared because both screens sort the same table. Keeping the API name separate from the
     * entity path means callers never name an internal path, and adding a column here is a
     * deliberate act rather than a side effect of an entity rename.
     */
    static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "submittedAt", "submittedAt",
            "readinessStatus", "readinessStatus",
            "status", "status",
            // Dot paths: Spring Data resolves these by joining `bookings` (and `customers`). All
            // three are @ManyToOne, so a join added for sorting cannot multiply rows the way a
            // to-many path would.
            "arrivalDate", "booking.checkInDate",
            "bookingCode", "booking.bookingCode",
            "customerName", "booking.customer.fullName");

    private HandoverListQuery() {
    }

    /**
     * @param allowedSorts API sort name → entity property path (dot-notation is resolved by Spring
     *                     Data, which joins as needed)
     */
    static Pageable pageable(String sortBy, String sortDir, int page, int size,
                             Map<String, String> allowedSorts, String defaultSort) {
        String requested = StringUtils.hasText(sortBy) ? sortBy.trim() : defaultSort;
        String property = allowedSorts.get(requested);
        if (property == null) {
            throw badRequest("INVALID_SORT_FIELD",
                    "Cannot sort by '" + requested + "'. Allowed: " + String.join(", ", allowedSorts.keySet()));
        }

        if (StringUtils.hasText(sortDir)
                && !"asc".equalsIgnoreCase(sortDir.trim())
                && !"desc".equalsIgnoreCase(sortDir.trim())) {
            throw badRequest("INVALID_SORT_DIRECTION",
                    "Sort direction must be 'asc' or 'desc', not '" + sortDir + "'.");
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir == null ? null : sortDir.strip())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        if (page < 0) {
            throw badRequest("INVALID_PAGE", "Page index cannot be negative.");
        }
        if (size < 1) {
            throw badRequest("INVALID_PAGE_SIZE", "Page size must be at least 1.");
        }
        if (size > MAX_PAGE_SIZE) {
            throw badRequest("INVALID_PAGE_SIZE",
                    "Page size cannot exceed " + MAX_PAGE_SIZE + " (requested " + size + ").");
        }

        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    /** Blank → {@code null} (no filter). A non-blank value that is not a constant → 400. */
    @SuppressWarnings("null")
    static <E extends Enum<E>> E enumFilter(Class<E> type, String value, String parameterName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw badRequest("INVALID_" + parameterName.toUpperCase(),
                    "Unknown " + parameterName + " '" + value.trim() + "'. Allowed: "
                            + String.join(", ", java.util.Arrays.stream(type.getEnumConstants())
                            .map(Enum::name).toList()));
        }
    }

    /** Blank → {@code null} (no filter). A non-blank value that is not ISO-8601 → 400. */
    static LocalDate dateFilter(String value, String parameterName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw badRequest("INVALID_" + parameterName.toUpperCase(),
                    "Could not read " + parameterName + " '" + value.trim() + "'. Expected YYYY-MM-DD.");
        }
    }

    /** Blank → {@code null} (no filter). A non-blank value that is not a UUID → 400. */
    static UUID uuidFilter(String value, String parameterName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw badRequest("INVALID_" + parameterName.toUpperCase(),
                    "Could not read " + parameterName + " '" + value.trim() + "'. Expected a UUID.");
        }
    }

    private static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }
}
