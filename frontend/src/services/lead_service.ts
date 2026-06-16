import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

// ── Types ─────────────────────────────────────────────────────────────────────

export type LeadStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "CONVERTED" | "LOST";

export type Lead = {
  leadId: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  source: string | null;
  status: LeadStatus;
  notes: string | null;
  convertedAt: string | null;
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
  source?: string;
  notes?: string;
  assignedUserId?: string;
};

export type UpdateLeadPayload = {
  fullName?: string;
  email?: string;
  phone?: string;
  companyName?: string;
  source?: string;
  status?: LeadStatus;
  notes?: string;
  assignedUserId?: string;
};

export type LeadListParams = {
  search?: string;
  status?: string;
  source?: string;
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
};
