"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  userService,
  roleService,
  permissionService,
  type UserListParams,
  type CreateUserPayload,
  type UpdateUserPayload,
  type CreateRolePayload,
} from "@/services/user_service";

// ── Users (UC-6.1 / 6.2 / 6.3) ─────────────────────────────────────────────────

export function useUserAccounts(params?: UserListParams) {
  return useQuery({
    queryKey: ["user-accounts", params],
    queryFn: () => userService.getList(params),
  });
}

export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateUserPayload) => userService.create(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["user-accounts"] });
      qc.invalidateQueries({ queryKey: ["roles"] }); // user counts change
      qc.invalidateQueries({ queryKey: ["users"] }); // assignee dropdowns
    },
  });
}

export function useUpdateUser(userId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateUserPayload) => userService.update(userId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["user-accounts"] });
      qc.invalidateQueries({ queryKey: ["roles"] });
      qc.invalidateQueries({ queryKey: ["users"] });
    },
  });
}

// ── Roles & Permissions (UC-6.4) ───────────────────────────────────────────────

export function useRoles() {
  return useQuery({
    queryKey: ["roles"],
    queryFn: () => roleService.getList(),
  });
}

export function usePermissions() {
  return useQuery({
    queryKey: ["permissions"],
    queryFn: () => permissionService.getList(),
  });
}

export function useCreateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateRolePayload) => roleService.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["roles"] }),
  });
}

export function useSetRolePermissions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ roleId, permissionIds }: { roleId: number; permissionIds: number[] }) =>
      roleService.setPermissions(roleId, permissionIds),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["roles"] }),
  });
}
