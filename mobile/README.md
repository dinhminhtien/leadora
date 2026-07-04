# Leadora Mobile (Flutter)

Enterprise, feature-first Flutter client for the Leadora CRM. Consumes the
Spring Boot backend (`../backend`, `/api/v1`).

## Tech stack

| Concern        | Choice |
|----------------|--------|
| State          | `flutter_riverpod` + `riverpod_generator` (AsyncNotifier-first) |
| Routing        | `go_router` (session-aware guard, nested tab shells, FCM deep links) |
| Networking     | `dio` + cache/retry interceptors, mutex-guarded token refresh seam |
| Serialization  | `json_serializable` / `freezed` |
| Storage        | `flutter_secure_storage` (tokens) · `shared_preferences` (prefs) |
| Auth           | JWT + `local_auth` biometric |
| Push / Monitor | `firebase_messaging` · `firebase_crashlytics` |
| i18n           | `intl` / gen-l10n (en, vi) |
| Testing        | `flutter_test` + `mocktail` |

## Architecture

Feature-first + trimmed Clean Architecture. `data` + `presentation` in every
feature; `domain/usecases` **only** for the complex flows (payment, booking,
interaction) — no ceremony for view-only screens.

```
lib/
├── core/            cross-cutting: config, network, routing, theme, l10n, widgets
├── features/<f>/    data/ · domain/(opt) · presentation/ · routes/
├── shared/          shared widgets, models, extensions
├── app.dart         root MaterialApp.router
├── bootstrap.dart   composition root (error handlers, ProviderScope)
└── main.dart        entrypoint → bootstrap()
```

## Flavors / config

No dotenv. Config is compile-time via `--dart-define-from-file`:

```bash
flutter run       --dart-define-from-file=config/dev.json
flutter run       --dart-define-from-file=config/staging.json
flutter build apk --dart-define-from-file=config/prod.json
```

Values are read through `core/config/env.dart` (`Env.apiBaseUrl`, `Env.flavor`, …).
`config/dev.json` points the API at `10.0.2.2:8085` (Android emulator → host `localhost`).

## Commands

```bash
flutter pub get
flutter gen-l10n                         # regenerate localizations
dart run build_runner build -d           # codegen (riverpod/json/freezed) — Phase 2+
flutter analyze
flutter test
```

## Backend integration notes

- Response envelope: `{ success, message, errorCode, details, data, timestamp }`
  (mirrors backend `common/response/ApiResponse`).
- **Auth reality:** backend `/auth/login` returns a single **24h HS256 access
  token** and exposes **no refresh token / no `/auth/refresh`**. The Dio
  refresh interceptor + mutex are built as required, but wired to force
  re-login on 401. The seam (`ApiPaths.refresh`, `TokenRefresher`) is where a
  real refresh endpoint drops in with zero UI changes.

## Build phases

1. ✅ Foundation — structure, flavors, routing, theme, l10n
2. ⬜ Core infrastructure — Dio, interceptors, storage, logger, crashlytics, exceptions
3. ⬜ Auth — login, biometric, session guard, current user
4. ⬜ Booking — optimistic UI, idempotency, offline retry queue, polling
5. ⬜ Payment — QR, status polling, state machine, timeout/retry
6. ⬜ Notification — FCM foreground/background/terminated, deep links, unread badge
7. ⬜ Testing — mocktail, auth/booking/payment/repository
8. ⬜ Optimization — skeletons, empty/error states, pull-to-refresh
