import type { ReactNode } from "react";
import { useAuth } from "./AuthProvider";

export function Can({ permission, children, fallback = null }: { permission: string | string[]; children: ReactNode; fallback?: ReactNode }) {
  const { hasPermission } = useAuth();
  return hasPermission(permission) ? children : fallback;
}
