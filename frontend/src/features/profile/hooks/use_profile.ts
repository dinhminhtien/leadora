"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  profileService,
  type UpdateProfilePayload,
  type ChangePasswordPayload,
} from "@/services/profile_service";

export function useMyProfile() {
  return useQuery({
    queryKey: ["profile", "me"],
    queryFn: () => profileService.getMe(),
    select: (res) => res.data,
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateProfilePayload) => profileService.updateMe(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile", "me"] });
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (payload: ChangePasswordPayload) => profileService.changePassword(payload),
  });
}
