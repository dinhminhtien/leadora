# CLAUDE.md — Leadora CRM Development Guide

Leadora is a modular hotel & hospitality CRM platform built with a Spring Boot backend, Next.js frontend, and AI-powered assistant features.

## Project Skills (.claude/skills/)

Repo-specific skills; invoke with `/name` or let them auto-trigger. Prefer them over improvising the same workflow from scratch:

| Skill | Use when |
|---|---|
| `/verify` | Before committing any nontrivial change — exact analyze/test/build commands and pass criteria per stack |
| `/implement-uc` | Implementing or extending a UC-xx / business feature — design-doc reading order, BR/RBAC non-negotiables, build order (backend contract first) |
| `/api-contract` | An endpoint, DTO, enum, or query param changes — alignment checklist across backend ↔ web ↔ mobile plus known wire-format pitfalls |
| `/run-stack` | Running/demoing locally or debugging 401/CORS/connection issues between backend :8085, frontend :3000, and the Flutter app |
| `/mobile-ui` | Improving/redesigning the Flutter app UI, building a screen/widget, or touching `mobile/lib/core/theme` or `shared/widgets` — design tokens, component polish, and emulator visual verification |

## Workspace Overview

```
leadora/
  ├── backend/          Spring Boot 3.5.14 modular monolith (Java 21)
  ├── frontend/         Next.js 16 web dashboard (React 19, TypeScript)
  ├── mobile/           Flutter mobile app workspace (reserved)
  ├── ai-service/       Python/FastAPI internal AI service (reserved)
  ├── docs/             DB migrations, architecture notes
  └── .env              Shared environment configuration (DO NOT COMMIT)
```

All services share one .env file at repo root. Backend reads via `optional:file:../.env[.properties]`; frontend loads in `next.config.ts`.

---

## Backend: Spring Boot Modular Monolith

**Port:** 8085 | **Java:** 21 | **Framework:** Spring Boot 3.5.14 with Spring AI

### Package Structure

```
com.novax.leadora
├── api/                REST controllers, DTOs, exception mapping
│   ├── controller/     Business operations
│   └── dto/
│       ├── request/    Input validation DTOs (@NotBlank, @Email, @Size)
│       └── response/   Output DTOs (with .from() mapper methods)
├── application/
│   └── usecase/        One UseCase per business operation (@Service, @Transactional)
├── domain/             (Reserved for business rules)
├── infrastructure/
│   ├── persistence/
│   │   ├── entity/     JPA @Entity classes (Lombok)
│   │   │   ├── enums/  LeadStatus, QuotationStatus, etc.
│   │   │   └── BaseEntity (auto-managed createdAt, updatedAt)
│   │   └── repository/ Spring Data JPA repositories
│   ├── integration/    External integrations
│   └── scheduler/      Scheduled tasks
├── security/           JWT, RBAC, permission checks
├── config/             Spring security, JPA auditing config
└── common/             ApiResponse wrapper, shared exceptions
```

### Key Dependencies

- Spring Data JPA (persistence & repositories)
- Spring Security + OAuth2 (JWT/RBAC)
- Spring AI 1.1.7 (Gemini chat, embeddings, pgvector RAG)
- PostgreSQL JDBC
- Lombok (reduce boilerplate)

### Database

- **System:** PostgreSQL (Supabase or local Docker)
- **ORM:** JPA/Hibernate with UUID keys and OffsetDateTime
- **Migrations:** Flyway (docs/migrations/V*.sql)

**Env Vars:**
```
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=secret
SPRING_JPA_DDL_AUTO=validate
```

### Authentication

- **JWT Provider:** Supabase (HS256 symmetric)
- **Token:** Injected as Bearer token on all authenticated requests
- **Session:** Stateless

**Public Endpoints:**
- POST `/api/v1/auth/login`
- POST `/api/v1/auth/forgot-password`
- POST `/api/v1/auth/reset-password`
- POST `/api/v1/auth/logout`

**Protected Endpoints:** All other `/api/v1/**` require Bearer token

### Build & Run

```powershell
cd backend
.\mvnw.cmd spring-boot:run    # Dev with hot reload
.\mvnw.cmd test               # Run tests
.\mvnw.cmd clean package      # Production build
```

### Common Backend Patterns

#### UseCase (Business Logic)

```java
@Slf4j @Service @RequiredArgsConstructor
public class CreateLeadUseCase {
  private final LeadRepository leadRepository;
  
  @Transactional
  public LeadResponse execute(CreateLeadRequest request) {
    LeadEntity lead = LeadEntity.builder()
      .fullName(request.getFullName())
      .status(LeadStatus.NEW)
      .build();
    LeadEntity saved = leadRepository.save(lead);
    return LeadResponse.from(saved);
  }
}
```

#### Entity (JPA)

```java
@Entity @Table(name = "leads")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeadEntity extends BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID leadId;
  
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_user_id")
  private UserEntity assignedUser;
  
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LeadStatus status;
}
```

#### Repository (Data Access)

```java
@Repository
public interface LeadRepository extends JpaRepository<LeadEntity, UUID> {
  @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
  @Query("SELECT l FROM LeadEntity l WHERE l.leadId = :leadId")
  Optional<LeadEntity> findWithUsersById(@Param("leadId") UUID leadId);
  
  Page<LeadEntity> searchLeads(@Param("search") String search, Pageable pageable);
}
```

#### DTO Request (Input Validation)

```java
@Getter @Setter
public class CreateLeadRequest {
  @NotBlank(message = "Full name required")
  @Size(max = 255)
  private String fullName;
  
  @Email
  private String email;
  
  private Boolean isCorporate;
}
```

#### DTO Response (Output Mapping)

```java
@Getter @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadResponse {
  private UUID leadId;
  private String fullName;
  private LeadStatus status;
  
  public static LeadResponse from(LeadEntity entity) {
    return LeadResponse.builder()
      .leadId(entity.getLeadId())
      .fullName(entity.getFullName())
      .status(entity.getStatus())
      .build();
  }
}
```

#### Controller (HTTP Layer)

```java
@RestController @RequestMapping("/api/v1/leads") @RequiredArgsConstructor
public class LeadController {
  private final CreateLeadUseCase createLeadUseCase;
  
  @PostMapping
  public ResponseEntity<ApiResponse<LeadResponse>> createLead(
    @Valid @RequestBody CreateLeadRequest request) {
    LeadResponse lead = createLeadUseCase.execute(request);
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(ApiResponse.success(lead, "Lead created successfully"));
  }
}
```

#### Enum (Business State)

```java
public enum LeadStatus {
  NEW, CONTACTED, QUALIFIED, CONVERTED, LOST
}
```

Stored as STRING in DB (not ordinal). Frontend mirrors as TypeScript union.

### AI Integration (Spring AI + Google Gemini)

**Feature:** Internal Sales Chat Assistant (UC-9)

- **Model:** Google Gemini (gemini-2.5-flash default, configurable)
- **Mode:** Vertex AI (GCP) or Gemini Developer API
- **Embeddings:** gemini-embedding-001 (768 dims)
- **Vector Store:** pgvector (PostgreSQL with HNSW index)

**Env Vars:**
```
GEMINI_USE_VERTEX=true
GOOGLE_GENAI_API_KEY=AIza...
GEMINI_PROJECT_ID=my-gcp-project
GEMINI_LOCATION=asia-southeast1
GEMINI_CHAT_MODEL=gemini-2.5-flash
GEMINI_CHAT_TEMPERATURE=0.2
GEMINI_THINKING_BUDGET=0
```

**Entities:**
- `AiChatSessionEntity` → Conversations
- `AiChatMessageEntity` → Chat turns
- `AiDocumentEntity` → Uploaded docs for RAG

---

## Frontend: Next.js + React

**Port:** 3000 | **Framework:** Next.js 16 (App Router), React 19, TypeScript 5

### Project Structure

```
frontend/src/
├── app/
│   ├── page.tsx              Root redirect to /dashboard
│   ├── (auth)/               Auth layout (no navbar)
│   │   └── login/page.tsx
│   ├── (dashboard)/          Dashboard layout (with navbar)
│   │   └── [feature]/page.tsx
│   ├── middleware.ts         Auth, redirects
│   ├── providers.tsx         Root providers (Supabase, React Query)
│   └── routes.ts             Route constants
├── components/
│   ├── ui/                   Button, Input, Select, Table, Card, etc.
│   ├── dashboard/
│   └── layout/
├── features/
│   └── [feature]/
│       ├── hooks/            use_[feature].ts (React Query)
│       └── screens/          Screens (ListScreen, DetailScreen)
├── services/
│   ├── api_client.ts         Axios with interceptors
│   ├── [feature]_service.ts  API calls (typed)
│   └── auth_service.ts       Auth wrapper
├── stores/
│   ├── auth_store.ts         Zustand (user, isAuthenticated)
│   ├── chat_store.ts         Chat state
│   └── ui_store.ts           UI toggles
├── shared/                   Shared types, utils
└── lib/                      Utilities
```

### Key Dependencies

- next 16.2.9 - App Router, SSR
- react 19.2.4 - UI
- react-hook-form 7.78.0 - Form state
- @hookform/resolvers 5.4.0 - Validation
- zod 4.4.3 - Schema validation
- axios 1.17.0 - HTTP client
- @tanstack/react-query 5.101.0 - Server state caching
- zustand 5.0.14 - Client state
- @supabase/supabase-js 2.108.1 - Auth, storage
- tailwindcss 4 - Styling
- lucide-react 1.17.0 - Icons

### Build & Run

```bash
cd frontend
npm install                # Install deps
npm run dev               # Dev server (http://localhost:3000)
npm run build             # Production build
npm start                 # Run prod server
npm run lint              # ESLint
```

### Env Vars

```
NEXT_PUBLIC_SUPABASE_URL=https://duqxepetrnpvfbzkugeu.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJ...
NEXT_PUBLIC_API_BASE_URL=http://localhost:8085/api/v1
```

Only `NEXT_PUBLIC_*` vars are inlined into client bundles.

### Common Frontend Patterns

#### Service (API Client Layer)

```typescript
import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type Lead = {
  leadId: string;
  fullName: string;
  status: string;
  createdAt: string;
};

export type CreateLeadPayload = {
  fullName: string;
  email?: string;
};

const ENDPOINT = "/leads";

export const leadService = {
  async getList(params?): Promise<ApiResponse<PageResponse<Lead>>> {
    const { data } = await apiClient.get(ENDPOINT, { params });
    return data;
  },

  async create(payload): Promise<ApiResponse<Lead>> {
    const { data } = await apiClient.post(ENDPOINT, payload);
    return data;
  },
};
```

#### React Query Hook

```typescript
"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { leadService } from "@/services/lead_service";

export function useLeads(params?) {
  return useQuery({
    queryKey: ["leads", params],
    queryFn: () => leadService.getList(params),
  });
}

export function useCreateLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => leadService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
    },
  });
}
```

#### Screen/Page Component

```typescript
"use client";

import { useLeads, useCreateLead } from "@/features/lead/hooks/use_leads";

export function LeadListScreen() {
  const { data: response, isLoading } = useLeads();
  const createMutation = useCreateLead();

  if (isLoading) return <div>Loading...</div>;

  return (
    <div className="p-6">
      <h1>Leads</h1>
      <button onClick={() => createMutation.mutate({ fullName: "John" })}>
        Create
      </button>
      <table>
        <tbody>
          {response?.data?.content?.map((lead) => (
            <tr key={lead.leadId}>
              <td>{lead.fullName}</td>
              <td>{lead.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

#### Zustand Store (Client State)

```typescript
import { create } from "zustand";

type AuthStore = {
  user: User | null;
  isAuthenticated: boolean;
  setUser: (user: User) => void;
  clearUser: () => void;
};

export const useAuthStore = create<AuthStore>((set) => ({
  user: null,
  isAuthenticated: false,
  setUser: (user) => set({ user, isAuthenticated: true }),
  clearUser: () => set({ user: null, isAuthenticated: false }),
}));
```

**Usage:** `const { user, setUser } = useAuthStore();`

#### API Client (Axios + Interceptors)

```typescript
import axios from "axios";

export type ApiResponse<T> = {
  success: boolean;
  message?: string;
  data: T;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  totalElements: number;
  totalPages: number;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8085/api/v1";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

// Inject Bearer token on every request
apiClient.interceptors.request.use(async (config) => {
  if (typeof window !== "undefined") {
    const isPublicAuth = config.url?.match(/auth\/(login|forgot-password|reset-password)/);
    if (!isPublicAuth) {
      let token = localStorage.getItem("accessToken");
      if (!token) {
        const supabase = createSupabaseBrowserClient();
        const { data: { session } } = await supabase.auth.getSession();
        token = session?.access_token;
      }
      if (token) config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Handle 401 logout
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== "undefined") {
      window.location.assign("/login");
    }
    return Promise.reject(error);
  }
);
```

---

## Database Schema

**System:** PostgreSQL | **Migrations:** Flyway (docs/migrations/V*.sql)

### Core Entities

- **users** → System users (staff)
- **leads** → Sales opportunities
- **customers** → Converted leads or direct customers
- **deals** → Business opportunities
- **quotations** → Price quotes
- **bookings** → Confirmed reservations
- **tasks** → Follow-ups
- **notifications** → System alerts
- **ai_chat_sessions** → Chat conversations
- **ai_chat_messages** → Chat turns
- **roles** & **permissions** → RBAC
- **sla_rules** & **sla_tracking** → SLA tracking

All entities have:
- `created_at` (auto-set by JPA Auditing)
- `updated_at` (auto-updated)
- UUID primary keys
- Foreign keys with LAZY loading

---

## Shared Environment Variables (.env)

```
# Supabase Auth
SUPABASE_URL=https://xyz.supabase.co
SUPABASE_ANON_KEY=eyJ...
SUPABASE_SERVICE_ROLE_KEY=eyJ...
SUPABASE_JWT_SECRET=AAVT3s...

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=secret

# Backend
PORT=8085
SPRING_PROFILES_ACTIVE=dev

# Frontend
NEXT_PUBLIC_API_BASE_URL=http://localhost:8085/api/v1
NEXT_PUBLIC_SUPABASE_URL=https://xyz.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJ...

# Gemini AI
GEMINI_PROJECT_ID=my-gcp-project
GOOGLE_GENAI_API_KEY=AIza...
GEMINI_CHAT_MODEL=gemini-2.5-flash
GEMINI_LOCATION=asia-southeast1

# Email
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=user@gmail.com
MAIL_PASSWORD=secret
```

---

## Git Workflow

**Branches:**
- `main` → Production (protected)
- `dev` → Integration
- Feature: `feature/UC-XX-description`

**Commit Format:**
```
feat(lead): create new lead with assignment [UC-8.1]
fix(quotation): correct discount calculation
docs: add architecture diagram
```

---

## Testing

### Backend
```powershell
.\mvnw.cmd test
.\mvnw.cmd test -Dtest=LeadControllerTests
```

### Frontend
```bash
npm test
npm run test:watch
```

---

## Deployment

### Backend
```bash
cd backend
.\mvnw.cmd clean package -DskipTests
java -jar target/leadora-0.0.1-SNAPSHOT.jar
```

### Frontend
```bash
cd frontend
npm run build
npm start
# Or: vercel deploy --prod
```

---

## Troubleshooting

**JWT validation failed (401):**
- Verify `SUPABASE_JWT_SECRET` matches Supabase settings
- Check Bearer token format: `Authorization: Bearer <token>`

**N+1 queries:**
- Use `@EntityGraph(attributePaths = {...})` on repositories
- Enable `spring.jpa.show-sql: true` to debug

**CORS blocked:**
- Verify `FRONTEND_URL` in backend config
- Check preflight (OPTIONS) requests allowed

**Frontend API calls fail:**
- Open DevTools → Network tab
- Verify `NEXT_PUBLIC_API_BASE_URL` points to correct backend

---

## Quick Start

```bash
# Clone & setup
git clone https://github.com/novax-ai/leadora.git
cd leadora

# Copy & fill .env
cp .env.example .env

# Backend
cd backend
.\mvnw.cmd spring-boot:run

# Frontend (new terminal)
cd frontend
npm install && npm run dev

# Open http://localhost:3000 → redirects to /dashboard
```

---

## Resources

- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring AI](https://github.com/spring-projects/spring-ai)
- [Next.js Docs](https://nextjs.org/docs)
- [TanStack React Query](https://tanstack.com/query)
- [Zustand](https://github.com/pmndrs/zustand)
- [Tailwind CSS](https://tailwindcss.com)
- [Supabase Docs](https://supabase.com/docs)

---

**Last Updated:** 2026-06-23  
**Version:** 0.0.1-SNAPSHOT  
**Maintainers:** Novax AI Team