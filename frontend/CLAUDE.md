# Leadora Frontend — CLAUDE.md

Next.js 16.2.9 / React 19.2.4 / TypeScript 5. Tailwind CSS 4. App Router. Runs on port **3000**.

> **Warning**: This is Next.js 16 — APIs and conventions may differ from what you know.
> Read `node_modules/next/dist/docs/` before writing any code that touches routing or server components.

## Commands

```bash
npm run dev      # start dev server :3000
npm run build    # production build
npm run lint     # ESLint
```

## Source layout

```
src/
  app/                      Next.js App Router root
    (auth)/                 auth route group (no dashboard chrome)
      login/page.tsx
      forgot-password/page.tsx
      reset-password/page.tsx
      layout.tsx
    (dashboard)/            dashboard route group (sidebar + header)
      dashboard/page.tsx
      leads/page.tsx
      leads/[id]/page.tsx
      deals/page.tsx
      sales-pipeline/page.tsx
      follow-up-tasks/page.tsx
      interaction-timeline/page.tsx
      notifications/page.tsx
      reminders/page.tsx
      sla/page.tsx
      quotations/page.tsx
      booking-confirmation/page.tsx
      reservation-status/page.tsx
      deposit-payment/page.tsx
      operational-handover/page.tsx
      front-office-handover/page.tsx
      customer-profiles/page.tsx
      customer-feedback/page.tsx
      ai-assistant/page.tsx
      reporting/page.tsx
      identity-access/page.tsx
      layout.tsx
    (public)/               unauthenticated pages
      feedback/[token]/page.tsx   public feedback form (BR-39)
      layout.tsx
    auth/callback/route.ts  Supabase OAuth callback
    layouts/                layout components
    middleware/             route guards
    providers/              context wrappers
    routes/                 route path constants
    layout.tsx              root layout
    page.tsx                home (redirects to /dashboard or /login)

  components/               reusable UI components
    dashboard/              dashboard-specific (KPICard, etc.)
    layout/                 Header, PageHeader
    ui/                     primitives: Badge, Button, Card, Input, Select, Table

  features/                 one folder per domain module
    <feature_name>/
      screens/              page-level React components (named *Screen.tsx)
      hooks/                data-fetching hooks (named use_*.ts)

  services/                 API clients
    api_client.ts           Axios instance + interceptors
    <feature>_service.ts    one file per feature
    supabase/               Supabase browser/server/middleware clients

  shared/
    components/             AppShell, EmptyState, LoadingState, StatusBadge, etc.
    constants/              roles.ts, routes.ts, statuses.ts
    mock/                   mockData.ts (temporary — migrate to real API calls)
    types/                  api.ts, auth.ts, role.ts, status.ts
    utils/                  cn.ts, error_handler.ts, format_date.ts

  stores/                   Zustand stores
    auth_store.ts           user + isAuthenticated + isLoading
    chat_store.ts           AI chat conversation state
    ui_store.ts             sidebar open/close, theme, etc.
```

## Key dependencies

| Package | Purpose |
|---|---|
| `next` 16.2.9 | App Router framework |
| `react` 19.2.4 | UI |
| `@supabase/ssr` + `@supabase/supabase-js` | Auth (Supabase) |
| `axios` | HTTP client for backend API |
| `@tanstack/react-query` v5 | Server state / data fetching |
| `zustand` v5 | Client state |
| `react-hook-form` + `zod` | Forms + validation |
| `@tanstack/react-table` v8 | Data tables |
| `@dnd-kit/*` | Drag-and-drop (Kanban pipeline) |
| `recharts` | Charts (reporting) |
| `lucide-react` | Icons |
| `tailwindcss` v4 | Styling |

## Auth flow

1. User logs in via Supabase Auth (email/password or OAuth).
2. Supabase returns a session with `access_token` (RS256 JWT).
3. `auth_store` holds the user object; `AuthProvider` syncs it with Supabase session on mount.
4. `api_client.ts` interceptor injects `Authorization: Bearer <token>` on every backend request.
5. Unauthenticated `401` responses → redirect to `/login`.
6. `middleware/auth_middleware.ts` and `middleware/permission_guard.ts` protect dashboard routes.

## API client pattern

```typescript
// Every backend call goes through apiClient from src/services/api_client.ts
import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

// Fetch list (paginated)
const res = await apiClient.get<ApiResponse<PageResponse<Lead>>>("/leads", { params: { page: 0, size: 20 } });

// Mutation
const res = await apiClient.post<ApiResponse<Lead>>("/leads", payload);
```

Backend base URL: `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8085/api/v1`).

## Data fetching pattern

Use React Query hooks inside `features/<feature>/hooks/use_*.ts`:

```typescript
// features/lead/hooks/use_leads.ts
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/services/api_client";

export function useLeads(page = 0) {
  return useQuery({
    queryKey: ["leads", page],
    queryFn: () => apiClient.get("/leads", { params: { page } }).then(r => r.data),
  });
}
```

## Feature module structure

Each feature follows the same pattern:

```
features/lead/
  screens/
    LeadListScreen.tsx      renders the list, uses hooks
    LeadDetailScreen.tsx    renders detail, uses hooks
  hooks/
    use_leads.ts            React Query hooks for CRUD
```

The `app/(dashboard)/leads/page.tsx` simply renders `<LeadListScreen />`.

## State management rules

- **Server state** (data from API): React Query only. Do not put API data in Zustand.
- **Auth state**: `auth_store` (Zustand). Synced by `AuthProvider`.
- **UI state** (sidebar, modals, theme): `ui_store` (Zustand).
- **Chat state**: `chat_store` (Zustand) — only for the AI assistant conversation buffer.

## Mock data migration

`src/shared/mock/mockData.ts` contains placeholder data. As each backend endpoint is implemented, replace the mock import with a real `useQuery` hook. Delete mock entries when fully migrated.

## Enum / status constants

Enums are string-literal unions in `src/shared/constants/statuses.ts`. Keep them in sync with the backend enums in `E:\Claude_Workspaces\enums-and-statuses.md` — the **Decision** lines there are authoritative.

## Public feedback page

`app/(public)/feedback/[token]/page.tsx` is unauthenticated. It identifies the customer only by the `token` URL param (maps to `sales_feedbacks.feedback_token`). No auth header sent here.

## Adding a new feature — checklist

1. Create `features/<feature_name>/screens/<Name>Screen.tsx`.
2. Create `features/<feature_name>/hooks/use_<feature>.ts` with React Query hooks.
3. Create `services/<feature>_service.ts` if complex API logic is needed.
4. Add page at `app/(dashboard)/<route>/page.tsx` that renders the screen.
5. Add the route constant to `shared/routes/route_paths.ts`.
6. Add any new status values to `shared/constants/statuses.ts` (match backend enum).
7. Add RBAC guard in `middleware/permission_guard.ts` if the route is role-restricted.
