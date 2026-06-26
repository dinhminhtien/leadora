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
  status?: LeadStatus;
  notes?: string;
  assignedUserId?: string;
};

export type ConvertLeadPayload = {
  customerType: CustomerType;
  fullName: string;
  email?: string;
  phone?: string;
  companyName?: string;
  taxCode?: string;
  address?: string;
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
  page?: number;
  size?: number;
};

// ── Service ───────────────────────────────────────────────────────────────────

const ENDPOINT = "/leads";

export const leadService = {
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
};
