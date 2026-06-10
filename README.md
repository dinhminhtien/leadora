# Leadora

Leadora is organized as a multi-application workspace for iterative delivery.

## Workspace Layout

```text
leadora/
  backend/        Spring Boot modular monolith
  frontend/       Next.js web dashboard workspace
  mobile/         Flutter mobile sales app workspace
  ai-service/     Python/FastAPI internal AI service workspace
  docs/           Architecture notes and package diagrams
```

## Current Status

- `backend/` contains the existing Spring Boot application and Maven build.
- `frontend/`, `mobile/`, and `ai-service/` are reserved workspaces for the client and AI runtimes.
- `docs/architecture/package-diagrams.md` records the intended package boundaries from the architecture diagrams.

## Backend Commands

Run backend commands from `backend/`:

```powershell
cd backend
.\mvnw.cmd test
```
