-- ============================================================================
-- User Management + RBAC permission redesign (feature/user-management, 2026-06-25)
-- Applied manually to the Supabase DB (ddl-auto=validate, no migration framework).
-- Kept here for traceability / reproducibility on a fresh database.
-- ============================================================================

-- 1) Last-login tracking (drives the 7-day idle → INACTIVE scheduler)
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at timestamptz;
UPDATE users SET last_login_at = now() WHERE last_login_at IS NULL;

-- 2) Permission catalog: module / action / dependency
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS module        varchar(50);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS action        varchar(20);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS label         varchar(120);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS depends_on_id integer REFERENCES permissions(permission_id);

-- Reseed the catalog cleanly (drops ad-hoc/junk rows + their grants)
DELETE FROM role_permissions;
DELETE FROM permissions;

INSERT INTO permissions (permission_code, module, action, label, created_at) VALUES
 ('LEAD_VIEW','LEAD','VIEW','View leads', now()),
 ('LEAD_WRITE','LEAD','WRITE','Create & edit leads', now()),
 ('CUSTOMER_VIEW','CUSTOMER','VIEW','View customers', now()),
 ('CUSTOMER_WRITE','CUSTOMER','WRITE','Create & edit customers', now()),
 ('TASK_VIEW','TASK','VIEW','View follow-up tasks', now()),
 ('TASK_WRITE','TASK','WRITE','Create & edit tasks', now()),
 ('PIPELINE_VIEW','PIPELINE','VIEW','View sales pipeline', now()),
 ('DEAL_VIEW','DEAL','VIEW','View deals', now()),
 ('DEAL_WRITE','DEAL','WRITE','Create & edit deals', now()),
 ('QUOTATION_VIEW','QUOTATION','VIEW','View quotations', now()),
 ('QUOTATION_WRITE','QUOTATION','WRITE','Create & edit quotations', now()),
 ('QUOTATION_APPROVE','QUOTATION','APPROVE','Approve / reject quotations', now()),
 ('INTERACTION_VIEW','INTERACTION','VIEW','View interaction timeline', now()),
 ('INTERACTION_WRITE','INTERACTION','WRITE','Log interactions', now()),
 ('BOOKING_VIEW','BOOKING','VIEW','View bookings', now()),
 ('BOOKING_WRITE','BOOKING','WRITE','Submit / process bookings', now()),
 ('RESERVATION_VIEW','RESERVATION','VIEW','View reservations', now()),
 ('RESERVATION_WRITE','RESERVATION','WRITE','Update reservations', now()),
 -- RoomRequestController enforces both of these via @access.can(...). They were missing
 -- from this catalog, which locked Sales out of raising a room request and Reservation
 -- out of answering one — the permission simply did not exist to be granted.
 ('ROOM_REQUEST_VIEW','ROOM_REQUEST','VIEW','View room requests', now()),
 ('ROOM_REQUEST_APPROVE','ROOM_REQUEST','APPROVE','Confirm / reject room requests', now()),
 ('HANDOVER_VIEW','HANDOVER','VIEW','View handovers', now()),
 ('HANDOVER_WRITE','HANDOVER','WRITE','Create & update handovers', now()),
 ('PAYMENT_VIEW','PAYMENT','VIEW','View payments', now()),
 ('PAYMENT_WRITE','PAYMENT','WRITE','Generate / update payments', now()),
 ('NOTIFICATION_VIEW','NOTIFICATION','VIEW','View notifications', now()),
 ('REMINDER_VIEW','REMINDER','VIEW','View reminders', now()),
 ('REMINDER_WRITE','REMINDER','WRITE','Create & update reminders', now()),
 ('REPORTING_VIEW','REPORTING','VIEW','View reports', now()),
 ('SLA_VIEW','SLA','VIEW','View SLA monitoring', now()),
 ('SLA_WRITE','SLA','WRITE','Configure SLA rules', now()),
 ('FEEDBACK_VIEW','FEEDBACK','VIEW','View customer feedback', now()),
 ('FEEDBACK_WRITE','FEEDBACK','WRITE','Update feedback review', now()),
 ('CHAT_VIEW','CHAT','VIEW','Use AI chat assistant', now());

-- Dependencies: every WRITE/APPROVE requires its module's VIEW
UPDATE permissions w
   SET depends_on_id = v.permission_id
  FROM permissions v
 WHERE v.module = w.module AND v.action = 'VIEW' AND w.action IN ('WRITE','APPROVE');

-- Default grants — SALES (Staff)
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE r.role_name = 'SALES'
   AND p.permission_code IN (
     'LEAD_VIEW','LEAD_WRITE','CUSTOMER_VIEW','CUSTOMER_WRITE','TASK_VIEW','TASK_WRITE',
     'PIPELINE_VIEW','DEAL_VIEW','DEAL_WRITE','QUOTATION_VIEW','QUOTATION_WRITE',
     'INTERACTION_VIEW','INTERACTION_WRITE','BOOKING_VIEW','BOOKING_WRITE',
     'RESERVATION_VIEW','RESERVATION_WRITE','HANDOVER_VIEW','HANDOVER_WRITE',
     'ROOM_REQUEST_VIEW',
     'PAYMENT_VIEW','PAYMENT_WRITE','NOTIFICATION_VIEW','REMINDER_VIEW','REMINDER_WRITE','CHAT_VIEW'
   );

-- Default grants — MANAGER (Staff set + approvals, reporting, SLA, feedback)
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE r.role_name = 'MANAGER'
   AND p.permission_code IN (
     'LEAD_VIEW','LEAD_WRITE','CUSTOMER_VIEW','CUSTOMER_WRITE','TASK_VIEW','TASK_WRITE',
     'PIPELINE_VIEW','DEAL_VIEW','DEAL_WRITE','QUOTATION_VIEW','QUOTATION_WRITE','QUOTATION_APPROVE',
     'INTERACTION_VIEW','INTERACTION_WRITE','BOOKING_VIEW','BOOKING_WRITE',
     'RESERVATION_VIEW','RESERVATION_WRITE','HANDOVER_VIEW','HANDOVER_WRITE',
     'PAYMENT_VIEW','PAYMENT_WRITE','NOTIFICATION_VIEW','REMINDER_VIEW','REMINDER_WRITE','CHAT_VIEW',
     'ROOM_REQUEST_VIEW',
     'REPORTING_VIEW','SLA_VIEW','SLA_WRITE','FEEDBACK_VIEW','FEEDBACK_WRITE'
   );

-- ----------------------------------------------------------------------------
-- The two operational desks. Both are permission-managed (RbacRoles.PERMISSION_MANAGED),
-- so a role with no role_permissions rows fails EVERY `@access.can(...)` check — it is
-- locked out of the whole API, not merely missing a screen. These blocks were absent, and
-- because the reseed above starts with `DELETE FROM role_permissions` that left Front
-- Office with zero permissions: all of UC-22.1/22.2/22.3 returned 403 to its own primary
-- actor, on a page the frontend still let them open (DASHBOARD_PATHS.FO is whitelisted to
-- avoid a redirect loop), so the desk rendered empty instead of saying Access Denied.
--
-- Each set is derived from the `@PreAuthorize` lists that actually name the role — nothing
-- broader. Grant a code no endpoint pairs with the role and it is dead weight; leave one
-- out and the endpoint is unreachable.
-- ----------------------------------------------------------------------------

-- Default grants — FO / Front Office desk (arrival handovers + settle payment at check-in).
-- `FRONT_OFFICE` is the legacy spelling of `FO`; both are accepted by the @PreAuthorize
-- lists and by RbacRoles, so grant to whichever the roles table actually holds.
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE r.role_name IN ('FO','FRONT_OFFICE')
   AND p.permission_code IN (
     -- UC-22.1 / 22.2 / 22.3 — the arrival-handover desk
     'HANDOVER_VIEW','HANDOVER_WRITE',
     -- UC-21.3 / 21.4 — view a payment and confirm it at the front desk
     'PAYMENT_VIEW','PAYMENT_WRITE',
     'NOTIFICATION_VIEW'
   );

-- Default grants — RESERVATION desk (answer room requests, then confirm/reject the booking).
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.role_id, p.permission_id, now()
  FROM roles r, permissions p
 WHERE r.role_name = 'RESERVATION'
   AND p.permission_code IN (
     'ROOM_REQUEST_VIEW','ROOM_REQUEST_APPROVE',
     'BOOKING_VIEW','BOOKING_WRITE',
     'RESERVATION_VIEW','RESERVATION_WRITE',
     'HANDOVER_VIEW','HANDOVER_WRITE',
     'PAYMENT_VIEW','PAYMENT_WRITE',
     'NOTIFICATION_VIEW'
   );

-- ADMIN holds every permission implicitly (no rows needed).

-- ----------------------------------------------------------------------------
-- Verification. Run after the grants above.
-- ----------------------------------------------------------------------------
-- 1) Every permission-managed role must hold at least one permission. A role listed here
--    with count 0 (or missing entirely) cannot use the API at all — that was the FO bug.
SELECT r.role_name, count(rp.permission_id) AS granted
  FROM roles r
  LEFT JOIN role_permissions rp ON rp.role_id = r.role_id
 WHERE upper(trim(r.role_name)) IN ('SALES','MANAGER','FO','FRONT_OFFICE','RESERVATION')
 GROUP BY r.role_name
 ORDER BY granted;

-- 2) Expect ZERO rows: every permission code the backend enforces via @access.can(...)
--    must exist in this catalog, or it can never be granted to anyone.
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
 WHERE NOT EXISTS (
   SELECT 1 FROM permissions p WHERE p.permission_code = enforced.code
 );
