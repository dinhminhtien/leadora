package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * What kind of work a follow-up task represents.
 *
 * <p>This used to be inferred by parsing the task title ("Call: ring the venue"),
 * which made the title load-bearing — renaming a task changed its type. It is now
 * a column of its own ({@code tasks.activity_type}); the title is just a title.
 *
 * <p>Stored as STRING, never ordinal, so reordering these constants is safe.
 */
public enum ActivityType {
    TASK,
    CALL,
    EMAIL,
    MEETING,
    SITE_VISIT,
    FOLLOW_UP;

    /** The value a task falls back to when none was chosen or stored. */
    public static final ActivityType DEFAULT = TASK;

    /**
     * Lenient parse of a wire value. Unknown, blank or null input yields
     * {@link #DEFAULT} rather than throwing — a client sending a type this build
     * doesn't know about should get a sanely-typed task, not a 500.
     */
    public static ActivityType fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        try {
            return ActivityType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    /**
     * Reads a possibly-null stored value. Rows written before the activity-type
     * migration can still hold NULL if the backfill has not run yet, and neither
     * the API nor the clients should ever see that.
     */
    public static ActivityType orDefault(ActivityType value) {
        return value != null ? value : DEFAULT;
    }
}
