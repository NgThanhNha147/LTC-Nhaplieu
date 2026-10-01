import { Navigate, Outlet, useLocation } from "react-router-dom";
import { PageLoading } from "../components/ApiState";
import { useAuth } from "./AuthProvider";

export function ProtectedRoute() {
  const { authenticated, loading } = useAuth();
  const location = useLocation();
  if (loading) return <PageLoading />;
  if (!authenticated) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  return <Outlet />;
}

export function PermissionRoute({ permission }: { permission: string | string[] }) {
  const { hasPermission } = useAuth();
  if (!hasPermission(permission)) return <Navigate to="/forbidden" replace />;
  return <Outlet />;
}
