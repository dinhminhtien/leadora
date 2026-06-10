# Leadora AI Service

Reserved workspace for the internal Python/FastAPI AI runtime.

Suggested first-level layout when the service is scaffolded:

```text
api/
application/
intent/
retrieval/
security/
llm/
session/
persistence/
```

This service should expose only internal APIs to the Spring Boot backend and keep CRM access read-scoped through backend-authorized context.
