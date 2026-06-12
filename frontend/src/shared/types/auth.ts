import type { Role } from "@/shared/types/role";

export type User = {
  id: string;
  email: string;
  name: string;
  roles: Role[];
};

export type AuthSession = {
  user: User;
  isAuthenticated: boolean;
};

export type LoginCredentials = {
  email: string;
  password: string;
};
