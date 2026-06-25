# Leadora

Leadora is organized as a multi-application workspace for iterative delivery.

## Workspace Layout

```text
leadora/
  backend/        Spring Boot modular monolith
  frontend/       Next.js web dashboard workspace
  mobile/         Flutter mobile sales app workspace
  docs/           Architecture notes and package diagrams
```

## Current Status

- `backend/` contains the existing Spring Boot application and Maven build.
- `frontend/` and `mobile/` are the client workspaces. The internal AI chat assistant runs inside `backend/` (Spring AI + Gemini), so the separate `ai-service/` placeholder has been removed.
- `docs/architecture/package-diagrams.md` records the intended package boundaries from the architecture diagrams.

## Backend Commands

Run backend commands from `backend/`:

```powershell
cd backend
.\mvnw.cmd test
```
