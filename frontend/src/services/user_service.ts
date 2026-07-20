import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

// ── Types ─────────────────────────────────────────────────────────────────────

export type UserStatus = "ACTIVE" | "INACTIVE" | "LOCKED";

export type UserAccount = {
  userId: string;
  fullName: string;
  email: string;
  phone: string | null;
  roleId: number | null;
  roleName: string | null;
  status: UserStatus;
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateUserPayload = {
  fullName: string;
  email: string;
  password: string;
  phone?: string;
  roleId: number;
  status?: UserStatus;
  avatarUrl?: string;
};

export type UpdateUserPayload = {
  fullName?: string;
  email?: string;
  password?: string;
  phone?: string;
  roleId?: number;
  status?: UserStatus;
  avatarUrl?: string;
};

/** Lightweight row from the flat GET /users endpoint (assignee dropdowns — any role). */
export type UserSummary = {
  userId: string;
  fullName: string;
  email: string;
  roleName: string | null;
};

export type UserListParams = {
  search?: string;
  roleId?: number;
  status?: string;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  page?: number;
  size?: number;
};

export type Permission = {
  permissionId: number;
  permissionCode: string;
  description: string | null;
  module: string | null;
  action: string | null;
  label: string | null;
  dependsOnId: number | null;
};

export type Role = {
  roleId: number;
  roleName: string;
  description: string | null;
  userCount: number;
  permissions: Permission[];
};

export type CreateRolePayload = {
  roleName: string;
  description?: string;
  permissionIds?: number[];
};

// ── Services ──────────────────────────────────────────────────────────────────

const USERS = "/users";
const ROLES = "/roles";
const PERMISSIONS = "/permissions";

export const userService = {
  // UC-6.1 — paged management list (Admin only — non-admin callers get 403;
  // use getSummaries() for assignee dropdowns instead)
  async getList(params?: UserListParams): Promise<ApiResponse<PageResponse<UserAccount>>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<UserAccount>>>(`${USERS}/accounts`, { params });
    return data;
  },

  // Flat, non-paged summary list for assignee dropdowns — open to all authenticated roles.
  async getSummaries(): Promise<ApiResponse<UserSummary[]>> {
    const { data } = await apiClient.get<ApiResponse<UserSummary[]>>(USERS);
    return data;
  },

  // UC-6.1 — detail
  async getById(id: string): Promise<ApiResponse<UserAccount>> {
    const { data } = await apiClient.get<ApiResponse<UserAccount>>(`${USERS}/${id}`);
    return data;
  },

  // UC-6.2 — create
  async create(payload: CreateUserPayload): Promise<ApiResponse<UserAccount>> {
    const { data } = await apiClient.post<ApiResponse<UserAccount>>(USERS, payload);
    return data;
  },

  // UC-6.3 — update
  async update(id: string, payload: UpdateUserPayload): Promise<ApiResponse<UserAccount>> {
    const { data } = await apiClient.put<ApiResponse<UserAccount>>(`${USERS}/${id}`, payload);
    return data;
  },
};

export const roleService = {
  // UC-6.4 — roles + their permissions
  async getList(): Promise<ApiResponse<Role[]>> {
    const { data } = await apiClient.get<ApiResponse<Role[]>>(ROLES);
    return data;
  },

  // UC-6.4 alt-flow A3 — create role
  async create(payload: CreateRolePayload): Promise<ApiResponse<Role>> {
    const { data } = await apiClient.post<ApiResponse<Role>>(ROLES, payload);
    return data;
  },

  // UC-6.4 — replace a role's permission set
  async setPermissions(roleId: number, permissionIds: number[]): Promise<ApiResponse<Role>> {
    const { data } = await apiClient.put<ApiResponse<Role>>(`${ROLES}/${roleId}/permissions`, { permissionIds });
    return data;
  },
};

export const permissionService = {
  async getList(): Promise<ApiResponse<Permission[]>> {
    const { data } = await apiClient.get<ApiResponse<Permission[]>>(PERMISSIONS);
    return data;
  },
};
