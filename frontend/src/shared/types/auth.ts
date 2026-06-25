import type { Role } from "@/shared/types/role";

export type User = {
  id: string;
  email: string;
  name: string;
  roles: Role[];
  /** Effective permission codes (e.g. LEAD_VIEW) used for frontend gating. */
  permissions?: string[];
};

export type AuthSession = {
  user: User;
  isAuthenticated: boolean;
};

export type LoginCredentials = {
  email: string;
  password: string;
};
