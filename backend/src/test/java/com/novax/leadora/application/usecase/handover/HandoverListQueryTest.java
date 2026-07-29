package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Request-parameter handling for the handover lists.
 *
 * <p>Two failure modes are pinned here. The loud one: an unknown {@code sortBy} used to reach
 * Spring Data and come back as HTTP 500. The quiet one, which is worse: an unrecognised filter
 * value was swallowed and became "no filter", so the endpoint answered with the entire table while
 * the caller believed it was filtered.
 */
class HandoverListQueryTest {

    private static final String DEFAULT_SORT = "createdAt";

    private Pageable pageable(String sortBy, String sortDir, int page, int size) {
        return HandoverListQuery.pageable(sortBy, sortDir, page, size,
                HandoverListQuery.SORTABLE, DEFAULT_SORT);
    }

    // ------------------------------------------------------------------ sorting (#7)

    @Test
    @DisplayName("A blank sortBy falls back to the default rather than exploding")
    void blankSortFallsBackToDefault() {
        for (String blank : new String[]{null, "", "   "}) {
            Sort.Order order = pageable(blank, "desc", 0, 10).getSort().getOrderFor("createdAt");
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        }
    }

    @ParameterizedTest(name = "sortBy={0} maps to {1}")
    @CsvSource({
            "createdAt,       createdAt",
            "submittedAt,     submittedAt",
            "readinessStatus, readinessStatus",
            "status,          status",
            "arrivalDate,     booking.checkInDate",
            "bookingCode,     booking.bookingCode",
            "customerName,    booking.customer.fullName",
    })
    @DisplayName("Whitelisted names map to entity paths, so the API never exposes one")
    void whitelistedSortsMapToEntityPaths(String apiName, String entityPath) {
        assertThat(pageable(apiName, "asc", 0, 10).getSort().getOrderFor(entityPath)).isNotNull();
    }

    @ParameterizedTest(name = "sortBy={0} is a 400, not a 500")
    @ValueSource(strings = {"nope", "password", "createdat", "booking.checkInDate", "' OR 1=1"})
    @DisplayName("An unknown sort field is rejected — it used to reach Spring Data and 500")
    void unknownSortFieldIsRejected(String sortBy) {
        assertThatThrownBy(() -> pageable(sortBy, "desc", 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                // the message must name the alternatives, or the caller cannot fix it
                .hasMessageContaining("arrivalDate");
    }

    @Test
    @DisplayName("Note the case-sensitivity: 'createdat' is refused rather than silently guessed")
    void sortFieldIsCaseSensitive() {
        assertThatThrownBy(() -> pageable("createdat", "desc", 0, 10))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> pageable("createdAt", "desc", 0, 10)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "sortDir={0} is accepted")
    @ValueSource(strings = {"asc", "ASC", "desc", "DESC", " asc "})
    void sortDirectionIsCaseInsensitive(String dir) {
        assertThatCode(() -> pageable("createdAt", dir, 0, 10)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A misspelled direction is refused, not silently treated as desc")
    void unknownSortDirectionIsRejected() {
        assertThatThrownBy(() -> pageable("createdAt", "ascending", 0, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("asc");
    }

    // ------------------------------------------------------------------ paging (#7)

    @ParameterizedTest(name = "page={0}, size={1} is rejected")
    @CsvSource({"-1, 10", "0, 0", "0, -5", "0, 101", "0, 100000"})
    @DisplayName("PageRequest.of used to throw IllegalArgumentException straight into a 500")
    void invalidPagingIsRejected(int page, int size) {
        assertThatThrownBy(() -> pageable("createdAt", "desc", page, size))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest(name = "page={0}, size={1} is accepted")
    @CsvSource({"0, 1", "0, 10", "5, 50", "0, 100"})
    void validPagingIsAccepted(int page, int size) {
        Pageable p = pageable("createdAt", "desc", page, size);
        assertThat(p.getPageNumber()).isEqualTo(page);
        assertThat(p.getPageSize()).isEqualTo(size);
    }

    // ------------------------------------------------------------------ filters (#8)

    @Test
    @DisplayName("A blank filter means 'no filter' — that part was always right")
    void blankFiltersAreIgnored() {
        for (String blank : new String[]{null, "", "  "}) {
            assertThat(HandoverListQuery.enumFilter(ReadinessStatus.class, blank, "readinessStatus")).isNull();
            assertThat(HandoverListQuery.dateFilter(blank, "arrivalDate")).isNull();
            assertThat(HandoverListQuery.uuidFilter(blank, "assignedFoUserId")).isNull();
        }
    }

    @ParameterizedTest(name = "readinessStatus={0} parses")
    @CsvSource({
            "PENDING_REVIEW,     PENDING_REVIEW",
            "reviewed,           REVIEWED",
            "  ready_for_arrival, READY_FOR_ARRIVAL",
    })
    void validEnumFilterParses(String raw, ReadinessStatus expected) {
        assertThat(HandoverListQuery.enumFilter(ReadinessStatus.class, raw, "readinessStatus"))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("A retired enum value is a 400 — it used to return the whole table")
    void retiredEnumValueIsRejected() {
        // 'READY' and 'IN_PROGRESS' were the old 3-state readiness model. Under the old code these
        // were swallowed, so the caller got every arrival back and read it as "all READY".
        for (String stale : new String[]{"READY", "IN_PROGRESS", "PENDING"}) {
            assertThatThrownBy(() ->
                    HandoverListQuery.enumFilter(ReadinessStatus.class, stale, "readinessStatus"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("READY_FOR_ARRIVAL"); // lists what IS valid
        }
    }

    @Test
    void handoverStatusFilterUsesTheSameRule() {
        assertThat(HandoverListQuery.enumFilter(HandoverStatus.class, "submitted", "status"))
                .isEqualTo(HandoverStatus.SUBMITTED);
        assertThatThrownBy(() -> HandoverListQuery.enumFilter(HandoverStatus.class, "OPEN", "status"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validDateFilterParses() {
        assertThat(HandoverListQuery.dateFilter("2026-07-29", "arrivalDate"))
                .isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(HandoverListQuery.dateFilter(" 2026-01-01 ", "arrivalDate"))
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @ParameterizedTest(name = "arrivalDate={0} is a 400")
    @ValueSource(strings = {"29/07/2026", "2026-13-01", "2026-02-30", "tomorrow", "2026-7-9"})
    @DisplayName("An unreadable date is refused instead of quietly dropping the filter")
    void invalidDateFilterIsRejected(String raw) {
        assertThatThrownBy(() -> HandoverListQuery.dateFilter(raw, "arrivalDate"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void validUuidFilterParses() {
        UUID id = UUID.randomUUID();
        assertThat(HandoverListQuery.uuidFilter(id.toString(), "assignedFoUserId")).isEqualTo(id);
    }

    @ParameterizedTest(name = "assignedFoUserId={0} is a 400")
    @ValueSource(strings = {"not-a-uuid", "12345", "'; DROP TABLE users; --"})
    void invalidUuidFilterIsRejected(String raw) {
        assertThatThrownBy(() -> HandoverListQuery.uuidFilter(raw, "assignedFoUserId"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UUID");
    }

    // ------------------------------------------------------------------ contract

    @Test
    @DisplayName("Every whitelisted sort name is a real, resolvable entity path")
    void sortableMapIsInternallyConsistent() {
        assertThat(HandoverListQuery.SORTABLE).isNotEmpty();
        HandoverListQuery.SORTABLE.forEach((apiName, entityPath) -> {
            assertThat(apiName).as("API sort name").isNotBlank().doesNotContain(" ");
            assertThat(entityPath).as("entity path for '%s'", apiName).isNotBlank();
        });
        // OpHandoverSpecificationTest pins the leaf attributes these paths walk.
        assertThat(HandoverListQuery.SORTABLE).containsKeys("createdAt", "arrivalDate");
    }
}
