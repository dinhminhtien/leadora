# Leadora Package Diagrams

This document records the intended workspace and package boundaries used for coding iterations.

## System Workspace

```mermaid
flowchart TB
  subgraph clients["Client Applications"]
    web["frontend\nWeb Dashboard (Next.js)"]
    mobile["mobile\nMobile Sales App (Flutter)"]
  end

  backend["backend\nSpring Boot Modular Monolith"]
  ai["ai-service\nInternal AI Service (FastAPI/Ollama/Qwen)"]
  external["External Integration Services\nEmail, Calendar, Payment Gateway"]
  data["Shared Data Storage\nPostgreSQL, Redis"]

  web -->|REST/JSON over HTTPS| backend
  mobile -->|REST/JSON over HTTPS| backend
  backend -->|authorized context / AI response| ai
  backend --> external
  backend --> data
  ai -->|read/write AI data| data
```

## Backend Package Diagram

```mermaid
flowchart TB
  root["com.novax.leadora"]

  root --> config
  root --> common
  root --> security
  root --> api
  root --> application
  root --> domain
  root --> infrastructure

  config["config\napplication, CORS, scheduler config"]
  common["common\nexceptions, responses, validation, constants, utilities"]
  security["security\nJWT, RBAC, auth filter, permissions, principal"]

  api["api\ncontrollers, request DTOs, response DTOs, API exception handler"]
  application["application\nuse cases, workflows, permission service, query service"]
  domain["domain\nfeature modules and business rules"]
  infrastructure["infrastructure\npersistence, integrations, scheduler, event bus, observability"]

  api --> application
  application --> domain
  application --> infrastructure
  infrastructure --> domain
  security --> api
  common --> api
  common --> application
  common --> domain
```

## Backend Domain Modules

```mermaid
flowchart LR
  domain["domain"] --> identityaccess
  domain --> leadcustomer
  domain --> quotation
  domain --> bookingoperations
  domain --> paymentdeposit
  domain --> operationalhandover
  domain --> customerfeedback
  domain --> chatassistant
  domain --> salespipelinedeal
  domain --> interactiontimeline
  domain --> followuptask
  domain --> notificationremindersla
  domain --> frontofficehandover
  domain --> reporting
  domain --> mobileactivity
  domain --> audit
```

## Frontend Package Target

```mermaid
flowchart TB
  frontend["frontend"] --> app
  frontend --> shared
  frontend --> features
  frontend --> services
  frontend --> stores
  frontend --> assets

  services --> api_client
  features --> auth
  features --> lead
  features --> quotation
  features --> booking_confirmation
  features --> ai_assistant
```

## Mobile Package Target

```mermaid
flowchart TB
  mobile["mobile"] --> assets
  mobile --> lib
  lib --> core
  lib --> routes
  lib --> features
  lib --> state
  lib --> data

  data --> api_client
  data --> repositories
  data --> models
  data --> local_storage
```

## AI Service Package Target

```mermaid
flowchart TB
  ai["ai-service"] --> api
  ai --> application
  ai --> intent
  ai --> retrieval
  ai --> security
  ai --> llm
  ai --> session
  ai --> persistence

  api --> application
  application --> intent
  application --> retrieval
  application --> llm
  application --> session
  security --> retrieval
  persistence --> intent
  persistence --> session
```
