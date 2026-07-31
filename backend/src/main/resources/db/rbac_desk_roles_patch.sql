-- ============================================================================
-- RBAC patch — desk roles (FO / RESERVATION) + the missing ROOM_REQUEST codes
-- ============================================================================
-- SAFE FOR A LIVE DATABASE. Purely additive and idempotent: every statement is
-- guarded by NOT EXISTS, nothing is deleted, and no existing grant is modified.
--
-- Use THIS file on a database that is already in service. Do NOT run
-- `user_management_rbac.sql` there — it opens with
--     DELETE FROM role_permissions;  DELETE FROM permissions;
-- which wipes every permission an Admin configured through UC-6.4. That file is
-- the fresh-database reseed; this one is the in-place fix.
--
-- What it repairs:
--  1. ROOM_REQUEST_VIEW / ROOM_REQUEST_APPROVE were enforced by RoomRequestController
--     via @access.can(...) but never existed in the `permissions` catalog, so they
--     could not be granted to anyone — Sales could not raise a room request and
--     Reservation could not answer one.
--  2. The FO and RESERVATION roles held ZERO rows in role_permissions. Both are in
--     RbacRoles.PERMISSION_MANAGED, so `@access.can(...)` returns false for every
--     code and the roles were locked out of the entire API — including all of
--     UC-22.1 / 22.2 / 22.3, whose primary actor IS Front Office.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1) Catalog: add the two missing permission codes
-- ----------------------------------------------------------------------------
INSERT INTO permissions (permission_code, module, action, label, created_at)
SELECT v.code, v.module, v.action, v.label, now()
  FROM (VALUES
    ('ROOM_REQUEST_VIEW',   'ROOM_REQUEST','VIEW',   'View room requests'),
    ('ROOM_REQUEST_APPROVE','ROOM_REQUEST','APPROVE','Confirm / reject room requests')
  ) AS v(code, module, action, label)
 WHERE NOT EXISTS (
   SELECT 1 FROM permissions p WHERE p.permission_code = v.code
 );

-- Same dependency rule as the reseed: every WRITE/APPROVE requires its module's VIEW.
-- Scoped to ROOM_REQUEST so an Admin's other rows are untouched.
UPDATE permissions w
   SET depends_on_id = v.permission_id
  FROM permissions v
 WHERE w.module = 'ROOM_REQUEST' AND w.action = 'APPROVE'
   AND v.module = 'ROOM_REQUEST' AND v.action = 'VIEW'
   AND w.depends_on_id IS DISTINCT FROM v.permission_id;

-- ----------------------------------------------------------------------------
-- 2) Grants. `role_permissions` has a composite PK (role_id, permission_id), so the
--    NOT EXISTS guard makes each block a no-op on re-run and never duplicates a row
--    an Admin already granted by hand.
-- ----------------------------------------------------------------------------

-- 2a) FO / Front Office desk. `FRONT_OFFICE` is the legacy spelling of `FO`; both are
--     accepted by the @PreAuthorize lists and by RbacRoles, so match either.
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE upper(trim(r.role_name)) IN ('FO','FRONT_OFFICE')
   AND p.permission_code IN (
     'HANDOVER_VIEW','HANDOVER_WRITE',     -- UC-22.1 / 22.2 / 22.3 arrival desk
     'PAYMENT_VIEW','PAYMENT_WRITE',       -- UC-21.3 / 21.4 settle payment at check-in
     'NOTIFICATION_VIEW'
   )
   AND NOT EXISTS (
     SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
   );

-- 2b) RESERVATION desk.
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE upper(trim(r.role_name)) = 'RESERVATION'
   AND p.permission_code IN (
     'ROOM_REQUEST_VIEW','ROOM_REQUEST_APPROVE',
     'BOOKING_VIEW','BOOKING_WRITE',
     'RESERVATION_VIEW','RESERVATION_WRITE',
     'HANDOVER_VIEW','HANDOVER_WRITE',
     'PAYMENT_VIEW','PAYMENT_WRITE',
     'NOTIFICATION_VIEW'
   )
   AND NOT EXISTS (
     SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
   );

-- 2c) SALES + MANAGER need ROOM_REQUEST_VIEW (RoomRequestController lists both roles);
--     it could not be granted before because the code did not exist.
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE upper(trim(r.role_name)) IN ('SALES','MANAGER')
   AND p.permission_code = 'ROOM_REQUEST_VIEW'
   AND NOT EXISTS (
     SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
   );

-- 2d) SALES needs REPORTING_VIEW for UC-23.2 (Follow-up Task Performance).
--     `ReportingController#getTaskPerformance` is guarded by
--     `hasAnyRole('SALES','MANAGER','ADMIN') and @access.can('REPORTING_VIEW')`, and the use case
--     narrows the query to the caller's own tasks for any non-Manager role — but SALES was never
--     granted the code, so the endpoint answered 403 to the actor it was written for and the
--     scoping branch was unreachable. The frontend gates /reporting on the same code, so the
--     screen did not open either. Manager/Admin scope is unaffected: the role list on the other
--     four reports still excludes SALES.
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE upper(trim(r.role_name)) = 'SALES'
   AND p.permission_code = 'REPORTING_VIEW'
   AND NOT EXISTS (
     SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
   );

COMMIT;

-- ============================================================================
-- VERIFICATION — run after COMMIT and read all three results.
-- ============================================================================

-- [V1] Expect ZERO rows. Any code listed here is enforced by the backend but absent
--      from the catalog, so no role can ever hold it.
SELECT code AS missing_from_catalog
  FROM (VALUES
    ('LEAD_VIEW'),('LEAD_WRITE'),('CUSTOMER_VIEW'),('CUSTOMER_WRITE'),
    ('TASK_VIEW'),('TASK_WRITE'),('PIPELINE_VIEW'),('DEAL_VIEW'),('DEAL_WRITE'),
    ('QUOTATION_VIEW'),('QUOTATION_WRITE'),('QUOTATION_APPROVE'),
    ('INTERACTION_VIEW'),('INTERACTION_WRITE'),('BOOKING_VIEW'),('BOOKING_WRITE'),
    ('RESERVATION_VIEW'),('RESERVATION_WRITE'),
    ('ROOM_REQUEST_VIEW'),('ROOM_REQUEST_APPROVE'),
    ('HANDOVER_VIEW'),('HANDOVER_WRITE'),('PAYMENT_VIEW'),('PAYMENT_WRITE'),
    ('NOTIFICATION_VIEW'),('REMINDER_VIEW'),('REMINDER_WRITE'),('REPORTING_VIEW'),
    ('SLA_VIEW'),('SLA_WRITE'),('FEEDBACK_VIEW'),('FEEDBACK_WRITE'),('CHAT_VIEW')
  ) AS enforced(code)
 WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.permission_code = enforced.code);

-- [V2] Every permission-managed role must hold at least one permission. A role showing
--      granted = 0 cannot use the API at all — that was the Front Office bug.
--      Expect roughly: FO 5, RESERVATION 11, SALES 26, MANAGER 32.
SELECT r.role_name, count(rp.permission_id) AS granted
  FROM roles r
  LEFT JOIN role_permissions rp ON rp.role_id = r.role_id
 WHERE upper(trim(r.role_name)) IN ('SALES','MANAGER','FO','FRONT_OFFICE','RESERVATION')
 GROUP BY r.role_name
 ORDER BY granted;

-- [V3] Expect ZERO rows: the exact codes the Front Office desk needs must all be present.
SELECT need.code AS fo_still_missing
  FROM (VALUES ('HANDOVER_VIEW'),('HANDOVER_WRITE'),
               ('PAYMENT_VIEW'),('PAYMENT_WRITE'),('NOTIFICATION_VIEW')) AS need(code)
 WHERE NOT EXISTS (
   SELECT 1
     FROM roles r
     JOIN role_permissions rp ON rp.role_id = r.role_id
     JOIN permissions p       ON p.permission_id = rp.permission_id
    WHERE upper(trim(r.role_name)) IN ('FO','FRONT_OFFICE')
      AND p.permission_code = need.code
 );
