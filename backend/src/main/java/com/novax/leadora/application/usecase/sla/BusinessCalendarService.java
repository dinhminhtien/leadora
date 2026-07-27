package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.repository.SlaHolidayRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves SLA deadlines against a configured working calendar (BR-32/BR-42):
 * business hours, business days and holidays — instead of continuous wall-clock hours.
 * Applies uniformly to every SLA rule (no per-rule calendar override).
 */
@Service
@RequiredArgsConstructor
public class BusinessCalendarService {

    private final SlaHolidayRepository slaHolidayRepository;

    @Value("${sla.business-hours.start:8}")
    private int startHour;

    @Value("${sla.business-hours.end:18}")
    private int endHour;

    @Value("${sla.business-hours.days:MON,TUE,WED,THU,FRI}")
    private String businessDaysCsv;

    private Set<DayOfWeek> businessDays;

    // Safety cap so a misconfigured calendar (e.g. no business days) fails fast
    // instead of looping forever.
    private static final int MAX_STEPS = 24 * 3650;

    // DayOfWeek.valueOf() only accepts full names (MONDAY, ...) — the config uses
    // the more natural 3-letter abbreviations, so map them explicitly.
    private static final Map<String, DayOfWeek> DAY_ABBREVIATIONS = Map.of(
            "MON", DayOfWeek.MONDAY,
            "TUE", DayOfWeek.TUESDAY,
            "WED", DayOfWeek.WEDNESDAY,
            "THU", DayOfWeek.THURSDAY,
            "FRI", DayOfWeek.FRIDAY,
            "SAT", DayOfWeek.SATURDAY,
            "SUN", DayOfWeek.SUNDAY);

    @PostConstruct
    void init() {
        businessDays = Arrays.stream(businessDaysCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BusinessCalendarService::parseDayOfWeek)
                .collect(Collectors.toSet());
    }

    private static DayOfWeek parseDayOfWeek(String value) {
        String key = value.toUpperCase();
        DayOfWeek abbreviation = DAY_ABBREVIATIONS.get(key);
        return abbreviation != null ? abbreviation : DayOfWeek.valueOf(key);
    }

    public OffsetDateTime addBusinessHours(OffsetDateTime start, int hours) {
        return shift(start, hours);
    }

    public OffsetDateTime subtractBusinessHours(OffsetDateTime start, int hours) {
        return shift(start, -hours);
    }

    private OffsetDateTime shift(OffsetDateTime start, int hours) {
        if (hours == 0) {
            return start;
        }
        int direction = hours > 0 ? 1 : -1;
        int remaining = Math.abs(hours);
        Set<LocalDate> holidays = slaHolidayRepository.findAll().stream()
                .map(h -> h.getHolidayDate())
                .collect(Collectors.toSet());

        OffsetDateTime cursor = start;
        int steps = 0;
        while (remaining > 0) {
            cursor = cursor.plusHours(direction);
            steps++;
            if (steps > MAX_STEPS) {
                throw new IllegalStateException(
                        "Business calendar could not resolve a deadline within " + MAX_STEPS
                                + " hours — check sla.business-hours configuration");
            }
            if (isBusinessHour(cursor, holidays)) {
                remaining--;
            }
        }
        return cursor;
    }

    private boolean isBusinessHour(OffsetDateTime dt, Set<LocalDate> holidays) {
        if (!businessDays.contains(dt.getDayOfWeek())) {
            return false;
        }
        if (dt.getHour() < startHour || dt.getHour() >= endHour) {
            return false;
        }
        return !holidays.contains(dt.toLocalDate());
    }
}
