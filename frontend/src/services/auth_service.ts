import { apiClient, type ApiResponse } from "@/services/api_client";
import type { LoginCredentials, User } from "@/shared/types/auth";

type AuthResult = {
  user: User;
  accessToken?: string;
};

export const authService = {
  async login(credentials: LoginCredentials) {
    const response = await apiClient.post<ApiResponse<AuthResult>>(
      "/auth/login",
      credentials,
    );
    return response.data;
  },

  async logout() {
    const response = await apiClient.post<ApiResponse<null>>("/auth/logout");
    return response.data;
  },

  async getProfile() {
    const response = await apiClient.get<ApiResponse<User>>("/auth/profile");
    return response.data;
  },
};
