---
name: run-stack
description: Start and wire the Leadora stack locally — backend :8085, frontend :3000, Flutter app against an emulator or Chrome — including env-file requirements and common startup failures. Use when asked to run/start/demo the app ("chạy app", "khởi động", "demo"), smoke-test a change end-to-end, or debug connection/401/CORS issues between the pieces.
---

# Run the Leadora stack locally

Everything reads the single shared `.env` at repo root (never commit it).
Backend loads it via `optional:file:../.env[.properties]`; frontend via
`next.config.ts`.

## Backend — port 8085

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Needs in `.env`: `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (Supabase Postgres
or local Docker), `SUPABASE_JWT_SECRET`, `GEMINI…`/`GOOGLE_GENAI_API_KEY`
(chat assistant). Health probe: any `/api/v1/**` returning **401 means the
server is up** (security filter answered) — it is not a startup failure.

Startup failures, in order of likelihood: DB unreachable (check
`SPRING_DATASOURCE_URL`), Flyway/`SPRING_JPA_DDL_AUTO=validate` schema
mismatch, port 8085 already bound.

## Frontend — port 3000

```bash
cd frontend
npm run dev
```

Needs `NEXT_PUBLIC_API_BASE_URL=http://localhost:8085/api/v1`,
`NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`. Only
`NEXT_PUBLIC_*` vars reach the browser bundle. `/` redirects to `/dashboard`;
unauthenticated → `/login`.

## Mobile (Flutter)

No dotenv — config is compile-time:

```bash
cd mobile
flutter run --dart-define-from-file=config/dev.json
```

- `config/dev.json` points at `http://10.0.2.2:8085` = host `localhost` from
  the **Android emulator**. For Chrome/Windows targets, override
  `API_BASE_URL` to `http://localhost:8085/api/v1` (edit dev.json or pass
  `--dart-define=API_BASE_URL=...` after the file, which wins).
- Changing config values requires a full restart (they are `const`),
  not hot reload.

## End-to-end smoke path

1. Backend up → 2. login via frontend or mobile (Supabase account with role
   SALES or MANAGER) → 3. exercise the changed flow (e.g. Leads: list →
   filter → create → detail → update status).

CORS errors in the browser: check `FRONTEND_URL` on the backend. 401 loops:
`SUPABASE_JWT_SECRET` mismatch between `.env` and the Supabase project.
