---
name: implement-uc
description: Workflow for implementing a Leadora use case (UC-xx) or business feature on any stack — where to read the design docs first, which business rules/enums/RBAC apply, and the order to build in. Use when asked to implement/extend a UC, add a feature ("thêm chức năng"), or when a task references UC numbers, BR rules, or the SRS/SDD.
---

# Implement a Leadora use case

## 0. Read the design references FIRST (they are NOT in this repo)

The condensed design docs live one level up, in `E:\Claude_Workspaces\`:

| File | Read it for |
|---|---|
| `business-rules.md` | BR-01–BR-44 (service-layer rules), MSG-01–MSG-31 (exact UI strings) |
| `enums-and-statuses.md` | Canonical enum values — but the **Java enums in code win** on conflict |
| `erd.md` | Table columns/constraints/FKs |
| `rbac-matrix.md` | Screen×role matrix — ⚠️ real DB roles are `ADMIN`/`SALES`/`MANAGER` (not `SALES_STAFF`) |
| `pipeline-and-workflow.md` | Lifecycle, stage transitions, notification triggers |

## 1. Check what already exists before writing anything

Features are often further along than the task list implies (mobile lead UCs
were already scaffolded when "requested"). Grep the target package/feature
folder and `git log --oneline -- <path>` before assuming greenfield.

## 2. Backend first — the contract is the spec

Layer order: entity/enum → repository → use case → DTO → controller.

Non-negotiables (each of these was a real bug once):
- **Business rules live in the use case**, not just client validation.
- **Every mutation use case needs the ownership check**: inject the module's
  `*AccessPolicy` and call `assertCanView(currentUser, entity)` after loading.
  List/detail having it does NOT mean update/convert/delete have it (IDOR).
- Status transitions are validated server-side (see `UpdateLeadUseCase` for
  the pattern: one-step-forward map + terminal states).
- Errors: throw `BusinessException` subclasses → `GlobalExceptionHandler`
  returns `{success,message,errorCode,details}`; put machine-usable payloads
  (e.g. an existing record's id) in `details`.
- Use MSG-xx strings verbatim for user-facing messages.

## 3. Then the client(s), mirroring the contract

- Frontend: service in `src/services/`, React Query hook in
  `src/features/<f>/hooks/`, screen in `src/features/<f>/screens/`.
- Mobile: models + repository in `lib/features/<f>/data/`, Riverpod
  providers/controllers in `presentation/providers/`, screens in
  `presentation/screens/`. Enum mirrors carry the wire string
  (`enum X { a('WIRE_VALUE') }`) and client-side transition rules must match
  the backend's exactly.
- Run the api-contract skill checklist whenever a DTO/param changes.

## 4. Verify and commit

- Verify per the `verify` skill.
- Commit format: `feat(module): summary [UC-xx.y]` / `fix(module): …`,
  branch `feature/<topic>`. `docs/` is git-ignored (`*docs/`) — never commit
  files there.
