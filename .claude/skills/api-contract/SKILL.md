---
name: api-contract
description: Checklist for keeping the REST contract aligned across backend DTOs, the Next.js frontend, and the Flutter mobile app — envelope/paging shapes, enum wire values, query-param and date/timezone pitfalls. Use when adding/changing an endpoint, DTO, enum, or query param; when a client shows undefined/null fields or silently-ignored filters; or when asked to "đối chiếu contract" / check API compatibility.
---

# Leadora API contract alignment

## Where each side of the contract lives

| Side | Location |
|---|---|
| Backend request/response | `backend/.../api/dto/{request,response}` (+ `common/response/ApiResponse`) |
| Backend params | controller `@RequestParam`/`@PathVariable` + the use case that consumes them |
| Frontend | `frontend/src/services/*_service.ts` types |
| Mobile | `mobile/lib/features/*/data/*_models.dart` + `core/constants/api_paths.dart` |

## Fixed shapes

- Envelope: `{ success, message?, data, errorCode?, details? }`.
  `details` is a *string* and may carry machine-usable payloads (e.g.
  `DUPLICATE_LEAD` → the existing lead's UUID). Clients must surface it
  (mobile: `ApiException.details`), not bury it in debug logs.
- Paging: Spring's native `Page` shape — `content`, `number`, `size`,
  `totalElements`, `totalPages`, `first`, `last`.
- Enums: `@Enumerated(EnumType.STRING)`; clients mirror the exact wire string
  (TS union / Dart enum with `wire` field). Unknown values should degrade
  gracefully client-side, never crash.

## Pitfalls that have actually bitten this codebase

1. **`+` in query strings decodes to a space.** Never send `+07:00`-style
   offsets as query params — send UTC instants with a `Z` suffix. Backend
   parsers should accept both ISO instants and legacy `yyyy-MM-dd`
   (see `GetLeadListUseCase.parseStartOfDay`).
2. **Date-only params are UTC calendar days server-side.** A client in
   UTC+7 must convert its *local* day boundary to a UTC instant, or leads
   created after midnight local fall out of the window.
3. **PUT endpoints are partial updates** (null field = unchanged, e.g.
   `UpdateLeadRequest`). Do not assume PATCH semantics need a new verb, and
   do not send empty strings where you mean "no change".
4. **Backend silently ignores malformed/unknown filter values** (bad status,
   bad date → dropped, not 400). When a filter "does nothing", suspect the
   value shape before the query logic.
5. **`scope` param only affects SALES callers** — MANAGER/ADMIN are always
   unscoped. Don't build client UI that implies otherwise for managers.
6. **Role names on `@PreAuthorize` are `SALES`/`MANAGER`/`ADMIN`.**

## Checklist when a contract changes

1. Update the backend DTO/param and its use case validation together.
2. Grep BOTH clients for the endpoint path and field names; update types,
   serializers, and any transition/derived logic that mirrors backend rules.
3. Keep old shapes accepted when a client can't ship in lockstep
   (dual-format parse > breaking change).
4. Add/extend a client unit test pinning the serialized shape
   (see `mobile/test/lead_test.dart` `toQuery` tests).
