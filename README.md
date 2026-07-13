# Leadora

Leadora is an enterprise-grade CRM system designed for iterative delivery. It features a modular monolith backend, a modern web dashboard, and a feature-first Flutter mobile application.

## Workspace Layout

```text
leadora/
  backend/        Spring Boot modular monolith
  frontend/       Next.js web dashboard workspace
  mobile/         Flutter mobile sales app workspace
  docs/           Architecture notes, package diagrams, and documentation
```

## Tech Stack Overview

### Backend
- Framework: Spring Boot 3.x, Spring Data JPA, Spring Security (OAuth2/JWT, RBAC)
- AI Integration: Spring AI, Google Cloud Vertex AI (Gemini 2.5 Flash, Google GenAI Embeddings)
- Databases: PostgreSQL (with pgvector for Vector Search) and Redis (caching and session management).
- Build Tool: Maven (using Maven Wrapper)

### Frontend
- Framework: Next.js (App Router), React, TypeScript
- Styling: Tailwind CSS
- Package Manager: npm / pnpm

### Mobile
- Framework: Flutter
- State Management: flutter_riverpod + riverpod_generator
- Routing: go_router
- Networking: dio (with cache/retry interceptors and token refresh)
- Serialization: json_serializable, freezed
- Auth & Storage: JWT, local_auth (biometric), flutter_secure_storage, shared_preferences

---

## Getting Started

### Prerequisites

To run the entire suite locally, make sure you have the following installed:
- Java JDK 17 or higher
- Node.js (v18 or higher) and npm/pnpm
- Flutter SDK (v3.x)
- Docker Desktop (for running database services)
- Google Cloud SDK (for Google Vertex AI authentication)

### Environment Configuration

Configure your environment variables by creating a `.env` file at the root of the workspace (`leadora/.env`). Both backend and frontend access this file.

Key variables required in `.env`:

```properties
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mydatabase
SPRING_DATASOURCE_USERNAME=myuser
SPRING_DATASOURCE_PASSWORD=secret

# Redis Configuration
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# Google Vertex AI Configuration
GEMINI_PROJECT_ID=leadora-499809
GEMINI_LOCATION=us-central1
GEMINI_CHAT_MODEL=gemini-2.5-flash
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
AI_EMBEDDING_DIMENSIONS=768
AI_VECTORSTORE_INIT_SCHEMA=true
AI_CHAT_DEV_USER_ID=
```

To authenticate with Vertex AI, run the following command on your local machine:
```powershell
gcloud auth application-default login
```

---

## Running the Services

### 1. External Services (Docker Compose)
Start the database, vector store, and caching services:
```powershell
cd backend
docker compose up -d
```
This launches pgvector (PostgreSQL) and Redis.

### 2. Backend Application
Run the Spring Boot backend server:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
By default, the REST API is exposed at `http://localhost:8085`.

To run backend tests:
```powershell
.\mvnw.cmd test
```

### 3. Frontend Web Dashboard
Run the development server for the Next.js dashboard:
```powershell
cd frontend
npm install
npm run dev
```
Open `http://localhost:3000` to view the web client dashboard.

### 4. Mobile App
Install dependencies and run the mobile client:
```powershell
cd mobile
flutter pub get
dart run build_runner build -d
flutter run --dart-define-from-file=config/dev.json
```

---

## Architectural & Design Guidelines

- Backend Packages: Focus on modular structure. Feature code goes inside dedicated modules under `domain/` or `application/` instead of cross-cutting service directories.
- Mobile Features: Organized using feature-first directory layout under `lib/features/`. Domain use-cases are reserved for complex operations like payment, booking, and complex integrations.
- Security: API requests use JWT Bearer authorization. Local storage manages secure JWT tokens, and biometric login checks user identity locally.
- Vector Store Schema: Note that switching embedding models changes vector dimensions. In case of dimension conflicts, drop the existing vector table (`leadora_vector_store`) and allow Spring AI to rebuild it using the current dimension (e.g., 768 for Gemini).

---

## Internal Sales Chat Assistant

Leadora includes an internal chat assistant designed to help sales teams retrieve CRM records, view summaries, and consult company policies through RAG.

### Intention Classification Workflow
- Mutation / Off-Topic: Requests to modify data (create, update, delete) or off-topic prompts are intercepted at the backend level.
- Assigned Data: Queries specific to the current sales representative retrieve record context scoped to the user ID.
- Team Summary: Requests for overall team performance fetch aggregated team metrics.
- Document Query: Technical questions refer to business documents that have been vectorized and stored in the PostgreSQL vector store.
