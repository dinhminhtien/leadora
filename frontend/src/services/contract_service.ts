import { apiClient, type ApiResponse } from "@/services/api_client";

export type ContractStatus = "DRAFT" | "SENT" | "ACKNOWLEDGED" | "ACTIVE" | "CANCELLED" | "SUPERSEDED" | "EXPIRED";
export type PdfStatus = "PENDING" | "GENERATING" | "READY" | "FAILED";
export type BillingMethod = "CREDIT_CARD" | "BANK_TRANSFER" | "CASH" | "DIRECT_BILLING";
export type CustomerType = "INDIVIDUAL" | "CORPORATE";

export type Contract = {
  id: string;
  contractCode: string;
  dealId: string;
  dealName?: string;
  quotationId: string;
  quotationCode?: string;
  customerId: string;
  customerName?: string;
  contactId?: string;
  contactName?: string;
  customerTypeSnapshot: CustomerType;
  billingMethod: BillingMethod;
  status: ContractStatus;
  pdfUrl?: string;
  pdfStatus: PdfStatus;
  version: number;
  sentAt?: string;
  sentTo?: string;
  acknowledgedAt?: string;
  effectiveDate?: string;
  validUntil?: string;
  commercialSnapshot?: any;
  createdAt: string;
  updatedAt: string;
};

export type UpdateBillingMethodRequest = {
  billingMethod: BillingMethod;
};

export type ConfirmOtpRequest = {
  otpCode: string;
};

export const contractService = {
  async getList(): Promise<ApiResponse<Contract[]>> {
    const response = await apiClient.get<ApiResponse<Contract[]>>("/contracts");
    return response.data;
  },

  async getById(id: string): Promise<ApiResponse<Contract>> {
    const response = await apiClient.get<ApiResponse<Contract>>(`/contracts/${id}`);
    return response.data;
  },

  async updateBillingMethod(id: string, billingMethod: BillingMethod): Promise<ApiResponse<Contract>> {
    const response = await apiClient.put<ApiResponse<Contract>>(`/contracts/${id}/billing-method`, { billingMethod });
    return response.data;
  },

  async send(id: string): Promise<ApiResponse<Contract>> {
    const response = await apiClient.post<ApiResponse<Contract>>(`/contracts/${id}/send`);
    return response.data;
  },

  async cancel(id: string): Promise<ApiResponse<Contract>> {
    const response = await apiClient.post<ApiResponse<Contract>>(`/contracts/${id}/cancel`);
    return response.data;
  },

  // Public Endpoints (Customer Portal)
  async publicGetById(id: string, token: string): Promise<ApiResponse<Contract>> {
    const response = await apiClient.get<ApiResponse<Contract>>(`/public/contracts/${id}?token=${token}`);
    return response.data;
  },

  async publicRequestOtp(id: string, token: string): Promise<ApiResponse<void>> {
    const response = await apiClient.post<ApiResponse<void>>(`/public/contracts/${id}/request-otp?token=${token}`);
    return response.data;
  },

  async publicConfirmOtp(id: string, token: string, otpCode: string): Promise<ApiResponse<Contract>> {
    const response = await apiClient.post<ApiResponse<Contract>>(`/public/contracts/${id}/confirm-otp?token=${token}`, { otpCode });
    return response.data;
  },
};
