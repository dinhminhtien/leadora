-- ============================================================================
-- UC-23.1 / UC-23.4 — deals.closed_at + deal_stage_history
--
-- RUN THIS BEFORE DEPLOYING THE MATCHING BUILD.
--   spring.jpa.hibernate.ddl-auto defaults to `validate` (application.yaml), and there is no
--   Flyway/Liquibase in this project. The new column and table exist in the JPA entities, so an
--   application started against a schema without them fails validation and does not come up at
--   all. Order is: run this script -> then deploy.
--
-- Safe to re-run: every statement is guarded.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1) deals.closed_at — when the deal reached CLOSED_WON / CLOSED_LOST.
--
-- Outcome metrics belong to the period a deal *closed*, not the period it was created. Until this
-- column existed, "win rate for July" meant "win rate of deals opened in July": it dropped a deal
-- opened in May and won in July, and diluted the rate with July's deals that are still in flight.
-- ----------------------------------------------------------------------------
ALTER TABLE deals ADD COLUMN IF NOT EXISTS closed_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_deals_closed_at ON deals (closed_at);

-- ----------------------------------------------------------------------------
-- 2) deal_stage_history — one append-only row per pipeline-stage transition.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS deal_stage_history (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id     uuid        NOT NULL REFERENCES deals (deal_id) ON DELETE CASCADE,
    from_stage  varchar(30),                 -- NULL = the deal entering the pipeline
    to_stage    varchar(30) NOT NULL,
    changed_at  timestamptz NOT NULL,
    changed_by  uuid REFERENCES users (user_id) ON DELETE SET NULL,
    source      varchar(30),                 -- CREATED | MANUAL | WORKFLOW_SYNC | AUTO_WIN | BACKFILL
    backfilled  boolean     NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_deal_stage_history_deal_changed
    ON deal_stage_history (deal_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_deal_stage_history_changed_at
    ON deal_stage_history (changed_at);

-- ----------------------------------------------------------------------------
-- 2b) Repairs for the case where Hibernate got here first.
--
-- With SPRING_JPA_DDL_AUTO=update (the setting used in dev), starting the app before running this
-- script auto-creates the column and the table from the JPA entities — so the CREATE above is a
-- no-op, but what Hibernate produces differs from what is wanted in two ways:
--
--   * `id` has no DEFAULT, because the application generates UUIDs in Java. Any INSERT from plain
--     SQL — the backfill below included — then fails on the NOT NULL.
--   * No foreign keys, because the entity maps deal_id / changed_by as plain UUID columns rather
--     than @ManyToOne associations. Deleting a deal would leave its history orphaned.
--
-- Both are fixed here rather than in the entity: adding a DB-side default costs the application
-- nothing (Hibernate still supplies its own value), and modelling the associations in JPA just to
-- get the constraints would pull two eagerly-fetchable entities into an append-only audit row.
-- ----------------------------------------------------------------------------
ALTER TABLE deal_stage_history ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE deal_stage_history ALTER COLUMN backfilled SET DEFAULT false;

-- ----------------------------------------------------------------------------
-- 3) activity_log indexes.
--
-- The backfill below reads activity_log by (entity_type, activity_type), and the table carries no
-- indexes at all today — the JPA entity declares none. Without these the backfill is a full scan,
-- and so is any future query that mines the log.
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_activity_log_entity
    ON activity_log (entity_type, entity_id, created_at);
CREATE INDEX IF NOT EXISTS idx_activity_log_type_created
    ON activity_log (activity_type, created_at);

COMMIT;

-- ============================================================================
-- BACKFILL — reconstructs what can be reconstructed. Run once, after the DDL above.
--
-- Both backfills are BEST-EFFORT and their limits matter:
--   * activity_log is appended AFTER COMMIT in a separate transaction
--     (ActivityLogListener + AppendActivityLogUseCase @Transactional(REQUIRES_NEW)), and several
--     publish sites swallow failures with a warning. A stage change whose log write failed left no
--     trace, so it cannot be recovered here.
--   * Deals that stopped moving before the activity-log feature shipped have no rows at all.
--   * DEAL_AUTO_WON only started carrying previousStage/newStage in this same change, so older
--     auto-wins are reconstructed from their status fields instead.
-- Rows written here are marked backfilled = true. Treat any metric dominated by them as indicative.
-- ============================================================================

BEGIN;

-- 3a) closed_at, most reliable source first.
--     Preference: the logged stage transition into a terminal stage; then the logged auto-win;
--     then updated_at as a last resort (a closed deal's last edit is usually the close itself).
WITH logged_close AS (
    SELECT a.entity_id AS deal_id,
           min(a.created_at) AS closed_at
      FROM activity_log a
     WHERE a.entity_type = 'DEAL'
       AND (
             (a.activity_type = 'DEAL_STAGE_UPDATED'
              AND a.payload->>'newStage' IN ('CLOSED_WON', 'CLOSED_LOST'))
          OR a.activity_type = 'DEAL_AUTO_WON'
           )
     GROUP BY a.entity_id
)
UPDATE deals d
   SET closed_at = COALESCE(l.closed_at, d.updated_at)
  FROM (SELECT deal_id, closed_at FROM logged_close) l
 WHERE d.deal_id = l.deal_id
   AND d.status IN ('WON', 'LOST')
   AND d.closed_at IS NULL;

-- Deals closed with no usable log entry: fall back to the last edit.
UPDATE deals
   SET closed_at = updated_at
 WHERE status IN ('WON', 'LOST')
   AND closed_at IS NULL;

-- 3b) deal_stage_history from the logged transitions.
INSERT INTO deal_stage_history (deal_id, from_stage, to_stage, changed_at, changed_by, source, backfilled)
SELECT a.entity_id,
       a.payload->>'previousStage',
       CASE
           -- Older auto-win rows recorded only the status pair; the stage is implied by it.
           WHEN a.activity_type = 'DEAL_AUTO_WON' AND a.payload->>'newStage' IS NULL THEN 'CLOSED_WON'
           ELSE a.payload->>'newStage'
       END,
       a.created_at,
       -- An actor that no longer exists must not block the insert (and would break the FK added
       -- after this step); the transition still happened, we just cannot name who made it.
       (SELECT u.user_id FROM users u WHERE u.user_id = a.actor_user_id),
       'BACKFILL',
       true
  FROM activity_log a
 WHERE a.entity_type = 'DEAL'
   AND a.activity_type IN ('DEAL_STAGE_UPDATED', 'DEAL_AUTO_WON')
   AND COALESCE(
           a.payload->>'newStage',
           CASE WHEN a.activity_type = 'DEAL_AUTO_WON' THEN 'CLOSED_WON' END
       ) IS NOT NULL
   AND EXISTS (SELECT 1 FROM deals d WHERE d.deal_id = a.entity_id)
   -- Idempotent: skip a transition already present for this deal at this instant.
   AND NOT EXISTS (
       SELECT 1 FROM deal_stage_history h
        WHERE h.deal_id = a.entity_id AND h.changed_at = a.created_at
   );

-- 3c) Opening row for every deal that has none, so time in the first stage is measurable.
--     The stage a deal started in is not recorded anywhere, so the earliest known from_stage is
--     used, falling back to the deal's current stage for deals that never moved.
INSERT INTO deal_stage_history (deal_id, from_stage, to_stage, changed_at, source, backfilled)
SELECT d.deal_id,
       NULL,
       COALESCE(
           (SELECT h.from_stage
              FROM deal_stage_history h
             WHERE h.deal_id = d.deal_id AND h.from_stage IS NOT NULL
             ORDER BY h.changed_at ASC
             LIMIT 1),
           d.pipeline_stage
       ),
       d.created_at,
       'BACKFILL',
       true
  FROM deals d
 WHERE NOT EXISTS (
       SELECT 1 FROM deal_stage_history h
        WHERE h.deal_id = d.deal_id AND h.from_stage IS NULL
   );

COMMIT;

-- ============================================================================
-- CONSTRAINTS — added after the backfill so a bad legacy row fails loudly here
-- rather than silently blocking the whole migration.
-- ============================================================================

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_deal_stage_history_deal') THEN
        ALTER TABLE deal_stage_history
            ADD CONSTRAINT fk_deal_stage_history_deal
            FOREIGN KEY (deal_id) REFERENCES deals (deal_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_deal_stage_history_user') THEN
        ALTER TABLE deal_stage_history
            ADD CONSTRAINT fk_deal_stage_history_user
            FOREIGN KEY (changed_by) REFERENCES users (user_id) ON DELETE SET NULL;
    END IF;
END $$;

COMMIT;

-- ============================================================================
-- VERIFICATION — read all four.
-- ============================================================================

-- [V1] Every closed deal should now carry a close timestamp. Expect 0.
SELECT count(*) AS closed_deals_without_closed_at
  FROM deals WHERE status IN ('WON','LOST') AND closed_at IS NULL;

-- [V2] No open deal should carry one. Expect 0.
SELECT count(*) AS open_deals_with_closed_at
  FROM deals WHERE status = 'OPEN' AND closed_at IS NOT NULL;

-- [V3] How much of the history is reconstructed rather than recorded. A high backfilled share
--      means stage-timing metrics are indicative only until live data accumulates.
SELECT backfilled, count(*) AS rows
  FROM deal_stage_history GROUP BY backfilled ORDER BY backfilled;

-- [V4] Coverage: deals with no history at all. Expect 0 after 3c.
SELECT count(*) AS deals_without_history
  FROM deals d
 WHERE NOT EXISTS (SELECT 1 FROM deal_stage_history h WHERE h.deal_id = d.deal_id);
