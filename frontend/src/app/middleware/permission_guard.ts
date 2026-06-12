import type { Role } from "@/shared/types/role";

export function canAccessRoute(pathname: string, roles: Role[] = []) {
  void pathname;
  void roles;

  return true;
}
