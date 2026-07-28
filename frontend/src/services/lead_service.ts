import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

// ── Types ─────────────────────────────────────────────────────────────────────

export type LeadStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "CONVERTED" | "LOST";

export type CustomerType = "INDIVIDUAL" | "CORPORATE";

export type Lead = {
  leadId: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  address: string | null;
  isCorporate: boolean;
  source: string | null;
  interestedService: string | null;
  status: LeadStatus;
  notes: string | null;
  convertedAt: string | null;
  customerId: string | null;
  assignedUserId: string | null;
  assignedUserName: string | null;
  createdById: string | null;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateLeadPayload = {
  fullName: string;
  email?: string;
  phone?: string;
  companyName?: string;
  address?: string;
  isCorporate?: boolean;
  source?: string;
  interestedService?: string;
  notes?: string;
  assignedUserId?: string;
};

export type UpdateLeadPayload = {
  fullName?: string;
  email?: string;
  phone?: string;
  companyName?: string;
  address?: string;
  isCorporate?: boolean;
  source?: string;
  interestedService?: string;
  status?: LeadStatus;
  notes?: string;
  assignedUserId?: string;
};

/**
 * UC-8.5 step 6 — the customer profile is built server-side from the lead, so the identity fields
 * are no longer sent. They used to be, copied straight off the lead by this client, which meant the
 * server trusted a payload it never checked against the record being converted.
 */
export type ConvertLeadPayload = {
  /** BR-09. Omit to inherit the lead's `isCorporate` flag. */
  customerType?: CustomerType;
  /** The one field with no counterpart on the lead. */
  taxCode?: string;
  /** BR-07: Sales Manager approval reason when converting a non-QUALIFIED lead. */
  reason?: string;
};

/** UC-8.5 exception E6 — attach the lead to a customer profile that already exists. */
export type LinkLeadToCustomerPayload = {
  customerId: string;
  reason?: string;
};

export type ConvertLeadResponse = {
  customerId: string;
  lead: Lead;
};

export type LeadListParams = {
  search?: string;
  status?: string;
  source?: string;
  isCorporate?: boolean;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  dateFrom?: string;
  dateTo?: string;
  /**
   * Owner view for a SALES caller (ignored for MANAGER/ADMIN, who see all leads):
   *   "assigned" (default) → leads assigned to me
   *   "created"            → leads I created (incl. the unassigned ones I just added)
   */
  scope?: "assigned" | "created";
  /**
   * Manager-only: restrict to leads with no owner yet — the queue still to be distributed.
   * Omitted rather than sent as `false` when off, so it never appears in the query string.
   */
  unassigned?: boolean;
  page?: number;
  size?: number;
};

/** Filters only — the stats endpoint is not paged and ignores sorting. */
export type LeadStatsParams = Omit<LeadListParams, "page" | "size" | "sortBy" | "sortDir">;

/**
 * Counts across every lead matching the current filters, not just the page on screen.
 *
 * The tiles used to be computed from `content` — the ten rows the list had loaded — so the numbers
 * moved when the user paged or re-sorted while the data stood still. Counting has to happen where
 * all the rows are.
 *
 * `convertedRate` / `lostRate` are percentages already rounded to one decimal, and are `null` when
 * there are no leads: "0.0%" would claim nothing converts, which is a different statement from
 * having nothing to measure.
 */
export type LeadStats = {
  total: number;
  converted: number;
  lost: number;
  active: number;
  qualified: number;
  convertedRate: number | null;
  lostRate: number | null;
};

// ── Service ───────────────────────────────────────────────────────────────────

const ENDPOINT = "/leads";

export const leadService = {
  async getStats(params?: LeadStatsParams): Promise<ApiResponse<LeadStats>> {
    const { data } = await apiClient.get<ApiResponse<LeadStats>>(`${ENDPOINT}/stats`, { params });
    return data;
  },

  async getList(params?: LeadListParams): Promise<ApiResponse<PageResponse<Lead>>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<Lead>>>(ENDPOINT, { params });
    return data;
  },

  async getById(id: string): Promise<ApiResponse<Lead>> {
    const { data } = await apiClient.get<ApiResponse<Lead>>(`${ENDPOINT}/${id}`);
    return data;
  },

  async create(payload: CreateLeadPayload): Promise<ApiResponse<Lead>> {
    const { data } = await apiClient.post<ApiResponse<Lead>>(ENDPOINT, payload);
    return data;
  },

  async update(id: string, payload: UpdateLeadPayload): Promise<ApiResponse<Lead>> {
    const { data } = await apiClient.put<ApiResponse<Lead>>(`${ENDPOINT}/${id}`, payload);
    return data;
  },

  async convert(id: string, payload: ConvertLeadPayload): Promise<ApiResponse<ConvertLeadResponse>> {
    const { data } = await apiClient.post<ApiResponse<ConvertLeadResponse>>(`${ENDPOINT}/${id}/convert`, payload);
    return data;
  },

  async linkCustomer(id: string, payload: LinkLeadToCustomerPayload): Promise<ApiResponse<ConvertLeadResponse>> {
    const { data } = await apiClient.post<ApiResponse<ConvertLeadResponse>>(`${ENDPOINT}/${id}/link-customer`, payload);
    return data;
  },
};
