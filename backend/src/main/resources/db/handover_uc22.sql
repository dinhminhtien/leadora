-- ============================================================================
-- Arrival Handover (UC-22.1 / UC-22.2 / UC-22.3) — op_handovers schema catch-up
-- ============================================================================
-- Commits 2b798a0 (readiness states + clarification note) and 3046f49 (FO assignee)
-- added entity fields WITHOUT a matching DDL script. The project runs
-- `ddl-auto=validate` (application.yaml) and has no migration framework, so on any
-- database that has not been ALTERed by hand the application FAILS TO START with a
-- SchemaManagementException.
--
-- Idempotent — safe to re-run. Apply before deploying the UC-22 feature.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) New columns
-- ----------------------------------------------------------------------------
-- OpHandoverEntity.clarificationNote — Front Office note required when readiness
-- is set to NEED_CLARIFICATION (UC-22.3 E7.2).
ALTER TABLE op_handovers ADD COLUMN IF NOT EXISTS clarification_note TEXT;

-- OpHandoverEntity.assignedFoUserId — the Front Office staff a handover is submitted
-- to (UC-20.4 requires it on submit; UC-22.1 step 3/4/5 scopes the FO desk by it).
-- Scalar UUID on the entity, but kept as a real FK so a deleted user cannot orphan it.
ALTER TABLE op_handovers ADD COLUMN IF NOT EXISTS assigned_fo_user_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_name = 'fk_op_handovers_assigned_fo_user'
           AND table_name = 'op_handovers'
    ) THEN
        ALTER TABLE op_handovers
            ADD CONSTRAINT fk_op_handovers_assigned_fo_user
            FOREIGN KEY (assigned_fo_user_id) REFERENCES users(user_id);
    END IF;
END $$;

-- The FO desk filters on this column on every list/summary call.
CREATE INDEX IF NOT EXISTS idx_op_handovers_assigned_fo_user
    ON op_handovers (assigned_fo_user_id);

-- OpHandoverEntity.version — @Version optimistic lock. Two Front Office staff saving different
-- readiness values for the same arrival were previously last-write-wins with neither one told.
ALTER TABLE op_handovers ADD COLUMN IF NOT EXISTS version INTEGER;
UPDATE op_handovers SET version = 0 WHERE version IS NULL;
ALTER TABLE op_handovers ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE op_handovers ALTER COLUMN version SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 2) ReadinessStatus: 3-state → 4-state (commit 2b798a0)
-- ----------------------------------------------------------------------------
-- was: PENDING | IN_PROGRESS | READY
-- now: PENDING_REVIEW | REVIEWED | READY_FOR_ARRIVAL | NEED_CLARIFICATION
--
-- Rows still holding an old value make Hibernate throw IllegalArgumentException the
-- moment they are read (@Enumerated(STRING) has no such constant), so existing data
-- must be migrated, not just the column definition.

-- 2a) The longest new value is NEED_CLARIFICATION (18 chars). The entity maps
--     length = 20; widen first in case the column was sized for the old values.
--     ADD first so the script also works on a schema that never had the column
--     (ALTER COLUMN ... TYPE on a missing column is a hard error).
ALTER TABLE op_handovers ADD COLUMN IF NOT EXISTS readiness_status VARCHAR(20);
ALTER TABLE op_handovers ALTER COLUMN readiness_status TYPE VARCHAR(20);

-- 2b) A CHECK constraint pinned to the old value list would reject the UPDATE below.
--     Drop any check constraint that mentions readiness_status; the enum is enforced
--     in the application layer (BR-38, @Enumerated(EnumType.STRING)).
DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT con.conname
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
         WHERE rel.relname = 'op_handovers'
           AND con.contype = 'c'
           AND pg_get_constraintdef(con.oid) ILIKE '%readiness_status%'
    LOOP
        EXECUTE format('ALTER TABLE op_handovers DROP CONSTRAINT %I', c.conname);
        RAISE NOTICE 'Dropped stale CHECK constraint %', c.conname;
    END LOOP;
END $$;

-- 2c) Map the old working states onto the new readiness model.
UPDATE op_handovers SET readiness_status = 'PENDING_REVIEW'    WHERE readiness_status = 'PENDING';
UPDATE op_handovers SET readiness_status = 'REVIEWED'          WHERE readiness_status = 'IN_PROGRESS';
UPDATE op_handovers SET readiness_status = 'READY_FOR_ARRIVAL' WHERE readiness_status = 'READY';

-- 2d) NOT NULL with a safe default for any row predating the column.
UPDATE op_handovers SET readiness_status = 'PENDING_REVIEW' WHERE readiness_status IS NULL;
ALTER TABLE op_handovers ALTER COLUMN readiness_status SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 3) Verification — every row must hold a value the Java enum knows.
--    Expect ZERO rows. Any row returned here will blow up on read.
-- ----------------------------------------------------------------------------
SELECT handover_id, readiness_status AS unknown_readiness_status
  FROM op_handovers
 WHERE readiness_status NOT IN
       ('PENDING_REVIEW','REVIEWED','READY_FOR_ARRIVAL','NEED_CLARIFICATION');

SELECT handover_id, status AS unknown_handover_status
  FROM op_handovers
 WHERE status NOT IN ('DRAFT','SUBMITTED','ACKNOWLEDGED','READY');
