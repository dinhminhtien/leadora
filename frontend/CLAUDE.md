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

## Implementation status

**Connected to real backend API:** `features/lead/` — `LeadListScreen`, `LeadDetailScreen`, `use_leads.ts`, `lead_service.ts`.

**Stub screens (UI shell only, no real API calls):** All other features have a `*Screen.tsx` and service file, but their service functions return placeholder data or call unimplemented backend endpoints.

**No mock data file:** `shared/mock/mockData.ts` has been deleted. Do not re-create it — implement real API hooks instead.

---

## Source layout

```
src/
  app/                        Next.js App Router root
    (auth)/                   auth route group (no dashboard chrome)
      login/page.tsx
      forgot-password/page.tsx
      reset-password/page.tsx
      layout.tsx
    (dashboard)/              dashboard route group (sidebar + header)
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
    (public)/                 unauthenticated pages
      feedback/[token]/page.tsx   public feedback form (BR-39)
      layout.tsx
    auth/callback/route.ts    Supabase OAuth callback
    layouts/                  AdminLayout, AuthLayout, DashboardLayout, PublicFeedbackLayout
    middleware/               auth_middleware.ts, permission_guard.ts
    providers/                AppProviders, AuthProvider, QueryProvider
    routes/                   protected_routes.ts, route_paths.ts
    layout.tsx                root layout
    page.tsx                  home → redirects to /dashboard or /login

  components/                 reusable UI primitives (not feature-specific)
    dashboard/                KPICard.tsx
    layout/                   Header.tsx, PageHeader.tsx
    ui/                       Badge, Button, Card, Input, Select, Table

  features/                   one folder per domain module
    lead/           ✅ API-connected
      hooks/use_leads.ts          useLeads, useLeadDetail, useCreateLead, useUpdateLead
      screens/LeadListScreen.tsx
      screens/LeadDetailScreen.tsx
    ai_assistant/   hooks/use_chat_sessions.ts + AiAssistantScreen.tsx
    auth/           screens: LoginScreen, ForgotPasswordScreen, ResetPasswordScreen
    booking_confirmation/   BookingConfirmationScreen.tsx
    customer_feedback/      CustomerFeedbackListScreen.tsx, SubmitFeedbackScreen.tsx
    customer_profile/       hooks/use_customer_profiles.ts + CustomerProfileListScreen.tsx
    deal/                   DealListScreen.tsx
    deposit_payment/        DepositPaymentScreen.tsx
    follow_up_task/         hooks/use_follow_up_tasks.ts + FollowUpTaskListScreen.tsx
    front_office_handover/  FrontOfficeHandoverScreen.tsx
    identity_access/        IdentityAccessScreen.tsx
    interaction_timeline/   InteractionTimelineScreen.tsx
    notification/           NotificationListScreen.tsx
    operational_handover/   OperationalHandoverScreen.tsx
    quotation/              QuotationListScreen.tsx
    reminder/               ReminderListScreen.tsx
    reporting/              DashboardScreen.tsx, ReportingScreen.tsx
    reservation_status/     ReservationStatusScreen.tsx
    sales_pipeline/         SalesPipelineScreen.tsx
    sla/                    SlaManagementScreen.tsx

  services/                   all 21 service files exist (stubs where backend not yet implemented)
    api_client.ts             Axios instance + interceptors — base URL from NEXT_PUBLIC_API_BASE_URL
    lead_service.ts           ✅ fully implemented
    auth_service.ts, booking_confirmation_service.ts, chat_assistant_service.ts,
    customer_feedback_service.ts, customer_profile_service.ts, deal_service.ts,
    deposit_payment_service.ts, follow_up_task_service.ts, front_office_handover_service.ts,
    identity_access_service.ts, interaction_timeline_service.ts, notification_service.ts,
    operational_handover_service.ts, quotation_service.ts, reminder_service.ts,
    reporting_service.ts, reservation_status_service.ts, sales_pipeline_service.ts,
    sla_service.ts, supabase_auth_service.ts
    supabase/                 client.ts, server.ts, middleware.ts

  shared/
    components/               AppShell, EmptyState, LoadingState, ModulePlaceholder,
                              PageHeader, StatusBadge
    constants/                roles.ts, routes.ts, statuses.ts
    types/                    api.ts, auth.ts, role.ts, status.ts
    utils/                    cn.ts, error_handler.ts, format_date.ts

  stores/
    auth_store.ts             user + isAuthenticated + isLoading
    chat_store.ts             AI chat conversation state
    ui_store.ts               sidebar open/close, theme, etc.
```

---

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

---

## Auth flow

1. User logs in via Supabase Auth (email/password or OAuth).
2. Supabase returns a session with `access_token` (RS256 JWT).
3. `auth_store` holds the user object; `AuthProvider` syncs it with Supabase session on mount.
4. `api_client.ts` interceptor injects `Authorization: Bearer <token>` on every backend request.
5. Unauthenticated `401` responses → redirect to `/login`.
6. `middleware/auth_middleware.ts` and `middleware/permission_guard.ts` protect dashboard routes.

---

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

---

## Data fetching pattern

Define types + service function in `services/<feature>_service.ts`, then create React Query hooks in `features/<feature>/hooks/use_<feature>.ts`:

```typescript
// services/lead_service.ts — defines types and calls apiClient
export const leadService = {
  async getList(params?: LeadListParams): Promise<ApiResponse<PageResponse<Lead>>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<Lead>>>(ENDPOINT, { params });
    return data;
  },
};

// features/lead/hooks/use_leads.ts — wraps service in React Query
export function useLeads(params?: LeadListParams) {
  return useQuery({
    queryKey: ["leads", params],
    queryFn: () => leadService.getList(params),
  });
}
```

---

## Feature module structure

```
features/lead/
  screens/
    LeadListScreen.tsx      renders the list; uses useLeads()
    LeadDetailScreen.tsx    renders detail; uses useLeadDetail(id)
  hooks/
    use_leads.ts            useLeads, useLeadDetail, useCreateLead, useUpdateLead
```

`app/(dashboard)/leads/page.tsx` simply renders `<LeadListScreen />`.

---

## State management rules

- **Server state** (API data): React Query only. Do not put API data in Zustand.
- **Auth state**: `auth_store` (Zustand). Synced by `AuthProvider`.
- **UI state** (sidebar, modals, theme): `ui_store` (Zustand).
- **Chat state**: `chat_store` (Zustand) — only for the AI assistant conversation buffer.

---

## Enum / status constants

Enums are string-literal unions in `src/shared/constants/statuses.ts`. Keep them in sync with the backend enums in `E:\Claude_Workspaces\enums-and-statuses.md` — the **Decision** lines there are authoritative.

---

## Public feedback page

`app/(public)/feedback/[token]/page.tsx` is unauthenticated. It identifies the customer only by the `token` URL param (maps to `sales_feedbacks.feedback_token`). No auth header sent here.

---

## Adding a new feature — checklist

> **All page routes and stub screens already exist.** Skip steps 3–4 for features that already have a page and screen file.

1. Implement `features/<feature_name>/screens/<Name>Screen.tsx` — replace placeholder UI with real data.
2. Create `features/<feature_name>/hooks/use_<feature>.ts` with React Query hooks.
3. ~~Create page~~ — `app/(dashboard)/<route>/page.tsx` already exists, just renders the screen.
4. ~~Create service file~~ — `services/<feature>_service.ts` already exists; implement the actual API calls.
5. Add any missing status values to `shared/constants/statuses.ts` (match backend enum).
6. Add RBAC guard in `middleware/permission_guard.ts` if the route is role-restricted.
