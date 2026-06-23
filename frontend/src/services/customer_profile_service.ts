import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type CustomerType = "INDIVIDUAL" | "CORPORATE";
export type CustomerStatus = "ACTIVE" | "INACTIVE";

export type Customer = {
  customerId: string;
  customerType: CustomerType;
  fullName: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  taxCode: string | null;
  address: string | null;
  status: CustomerStatus;
  assignedUserId: string | null;
  assignedUserName: string | null;
  createdById: string | null;
  createdByName: string | null;
  leadId: string | null;
  createdAt: string;
  updatedAt: string;
};

/** Lightweight type for autocomplete dropdowns (task forms etc.) */
export type CustomerSearchItem = {
  id: string;
  name?: string;
  email?: string;
  phone?: string;
  company?: string;
};

/** @deprecated use CustomerSearchItem — kept for backward compatibility */
export type CustomerProfile = CustomerSearchItem;

export type CreateCustomerPayload = {
  fullName: string;
  customerType: CustomerType;
  email?: string;
  phone?: string;
  companyName?: string;
  taxCode?: string;
  address?: string;
  assignedUserId?: string;
};

export type UpdateCustomerPayload = {
  fullName: string;
  customerType?: CustomerType;
  email?: string;
  phone?: string;
  companyName?: string;
  taxCode?: string;
  address?: string;
  status?: CustomerStatus;
  assignedUserId?: string;
};

export type CustomerHistoryItem = {
  type: "DEAL" | "BOOKING" | "QUOTATION";
  id: string;
  title: string;
  status: string | null;
  stage: string | null;
  amount: number | null;
  checkIn: string | null;
  checkOut: string | null;
  expectedClose: string | null;
  createdAt: string | null;
  notes: string | null;
};

export type CustomerListParams = {
  search?: string;
  customerType?: CustomerType | "";
  status?: CustomerStatus | "";
  sortBy?: string;
  sortDir?: "asc" | "desc";
  page?: number;
  size?: number;
};

const ENDPOINT = "/customers";

export const customerProfileService = {
  /** Autocomplete search — used in task/deal forms. */
  async getList(params?: { search?: string; size?: number }) {
    const response = await apiClient.get<ApiResponse<CustomerSearchItem[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  /** Full paginated list for the Customer Profiles screen. */
  async getCustomers(params?: CustomerListParams) {
    const response = await apiClient.get<ApiResponse<PageResponse<Customer>>>(
      `${ENDPOINT}/list`,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Customer>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: CreateCustomerPayload) {
    const response = await apiClient.post<ApiResponse<Customer>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: UpdateCustomerPayload) {
    const response = await apiClient.put<ApiResponse<Customer>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },

  async getStats() {
    const response = await apiClient.get<ApiResponse<{
      total: number; active: number; inactive: number;
      individual: number; corporate: number;
    }>>(`${ENDPOINT}/stats`);
    return response.data;
  },

  async getHistory(id: string) {
    const response = await apiClient.get<ApiResponse<CustomerHistoryItem[]>>(
      `${ENDPOINT}/${id}/history`,
    );
    return response.data;
  },
};
