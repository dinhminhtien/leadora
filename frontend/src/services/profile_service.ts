import { apiClient, type ApiResponse } from "@/services/api_client";

export type ProfileData = {
  userId: string;
  fullName: string;
  email: string;
  phone: string | null;
  roleName: string | null;
  status: "ACTIVE" | "INACTIVE" | "LOCKED";
  avatarUrl: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UpdateProfilePayload = {
  fullName: string;
  phone?: string | null;
  avatarUrl?: string | null;
};

export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
};

const ENDPOINT = "/profile";

export const profileService = {
  async getMe(): Promise<ApiResponse<ProfileData>> {
    const { data } = await apiClient.get<ApiResponse<ProfileData>>(`${ENDPOINT}/me`);
    return data;
  },

  async updateMe(payload: UpdateProfilePayload): Promise<ApiResponse<ProfileData>> {
    const { data } = await apiClient.put<ApiResponse<ProfileData>>(`${ENDPOINT}/me`, payload);
    return data;
  },

  async changePassword(payload: ChangePasswordPayload): Promise<ApiResponse<null>> {
    const { data } = await apiClient.put<ApiResponse<null>>(
      `${ENDPOINT}/me/change-password`,
      payload,
    );
    return data;
  },
};
