# Leadora Backend

Spring Boot modular monolith for the Leadora REST API.

## Package Layout

```text
com.novax.leadora
  api/             REST controllers, request DTOs, response DTOs, API exception mapping
  application/     use-case orchestration, workflows, permissions, read/query services
  domain/          feature modules and business rules
  infrastructure/  persistence, integrations, scheduler, event bus, observability
  security/        JWT, RBAC, filters, permission checks, authenticated principal
  config/          Spring configuration
  common/          shared exceptions, responses, validation, constants, utilities
```

The package skeleton is intentionally behavior-free. Add feature code inside the smallest matching module instead of creating cross-cutting service folders by default.

## Local Commands

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```
