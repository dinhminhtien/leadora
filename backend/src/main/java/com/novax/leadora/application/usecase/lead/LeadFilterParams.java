package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.specification.LeadSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The lead list's query string, parsed and validated once.
 *
 * <p><b>Why this is shared rather than parsed per endpoint.</b> The summary tiles and the table
 * below them must describe the same set of leads. If each endpoint read the query string its own
 * way — one tolerating a malformed date, the other rejecting it — the tiles would quietly report on
 * a different population than the rows underneath, and nothing would look wrong. One parser makes
 * that impossible by construction. It is the same reasoning as
 * {@code CustomerDuplicatePolicy}: a rule two callers must agree on does not belong to either.
 *
 * <p><b>Bad input is refused, not ignored.</b> Both the status and the dates used to fall back to
 * "no filter" when they could not be read, so {@code ?status=BOGUS} returned every lead and looked
 * exactly like a filter that matched everything. A filter that could not be applied must never be
 * indistinguishable from one that was.
 *
 * @param search      free text over name / email / company; empty string means no restriction
 * @param status      parsed status filter, or {@code null}
 * @param source      exact-match source filter; empty string means no restriction
 * @param isCorporate individual/organization filter, or {@code null}
 * @param dateFrom    inclusive lower bound on {@code createdAt}, or {@code null}
 * @param dateTo      inclusive upper bound on {@code createdAt}, or {@code null}
 */
public record LeadFilterParams(
        String search,
        LeadStatus status,
        String source,
        Boolean isCorporate,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo
) {

    public static LeadFilterParams parse(String search, String status, String source,
                                         Boolean isCorporate, String dateFrom, String dateTo) {
        return new LeadFilterParams(
                // "" rather than null so Hibernate 6 binds a varchar instead of bytea.
                StringUtils.hasText(search) ? search.trim() : "",
                parseStatus(status),
                StringUtils.hasText(source) ? source.trim() : "",
                isCorporate,
                parseStartOfDay(dateFrom),
                parseEndOfDay(dateTo));
    }

    /** The filters as a JPA specification; owner scope is supplied by the caller (BR-02). */
    public Specification<LeadEntity> toSpecification(boolean unscoped, UUID ownerId,
                                                    boolean createdByMe) {
        return LeadSpecification.filter(search, status, source, isCorporate,
                dateFrom, dateTo, unscoped, ownerId, createdByMe);
    }

    private static LeadStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return LeadStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_FILTER",
                    "Unknown lead status '" + status + "'. Valid values: "
                            + Arrays.stream(LeadStatus.values()).map(s -> s.name())
                                    .collect(Collectors.joining(", ")) + ".",
                    "status",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Two accepted shapes: a full ISO offset date-time, used as the exact instant the client chose
     * (mobile sends local midnight already converted to UTC), or a bare calendar date, read as an
     * inclusive UTC day.
     */
    private static OffsetDateTime parseStartOfDay(String date) {
        if (!StringUtils.hasText(date)) return null;
        String value = date.trim();
        OffsetDateTime exact = parseOffsetDateTime(value);
        if (exact != null) return exact;
        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ex) {
            throw malformedDate("dateFrom", value);
        }
    }

    private static OffsetDateTime parseEndOfDay(String date) {
        if (!StringUtils.hasText(date)) return null;
        String value = date.trim();
        OffsetDateTime exact = parseOffsetDateTime(value);
        if (exact != null) return exact;
        try {
            return LocalDate.parse(value).atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        } catch (Exception ex) {
            throw malformedDate("dateTo", value);
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static BusinessException malformedDate(String field, String value) {
        return new BusinessException("INVALID_FILTER",
                "Could not read " + field + " '" + value + "'. Use YYYY-MM-DD or a full ISO "
                        + "date-time such as 2026-07-27T00:00:00Z.",
                field,
                HttpStatus.BAD_REQUEST);
    }
}
