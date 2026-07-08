---
name: verify
description: Verify a Leadora change per stack (backend Spring Boot, frontend Next.js, mobile Flutter) with the exact commands and pass criteria. Use before committing any nontrivial change, when asked to "check", "verify", "chạy thử", "kiểm tra lại", or after fixing a bug to prove the fix.
---

# Verify a Leadora change

Run only the stacks the diff touches. A change that spans backend + a client
must verify BOTH sides (see the api-contract skill for alignment checks).

## Backend (Java 21 / Spring Boot 3.5)

```powershell
cd backend
.\mvnw.cmd -q compile        # fast structural check (~30s warm)
.\mvnw.cmd test              # full test run
```

- Bash tool equivalent: `./mvnw.cmd -q compile` works from Git Bash too.
- There is effectively no unit-test coverage (only `LeadoraApplicationTests`),
  so a green `test` proves little — compile + manual endpoint check is the
  real bar.
- Runtime verification needs the shared `.env` at repo root (DB, Supabase,
  Gemini). Endpoints are under `http://localhost:8085/api/v1/**` and almost
  all require a Supabase Bearer JWT (`@PreAuthorize hasAnyRole('SALES','MANAGER')`
  etc.) — an unauthenticated curl returning 401 is *expected*, not a failure.

## Frontend (Next.js 16)

```bash
cd frontend
npm run lint
npm run build      # catches type errors; there is no meaningful npm test
```

## Mobile (Flutter)

```bash
cd mobile
flutter analyze    # must end "No issues found!"
flutter test       # all green; lead model tests live in test/lead_test.dart
```

- `flutter analyze` may refresh `pubspec.lock` (pub get side effect). That
  lockfile churn is fine to commit.
- App-level smoke test: `flutter run --dart-define-from-file=config/dev.json`
  (Android emulator reaches host backend via `10.0.2.2:8085`).

## Pass criteria before commit

1. Touched stacks compile/analyze clean.
2. Tests that exist for the touched area pass.
3. If a REST contract changed: both producer and every consumer updated
   (backend DTO ↔ frontend `src/services/*` ↔ mobile `lib/features/*/data/*`).
4. State honestly in the summary what was verified statically vs end-to-end.
