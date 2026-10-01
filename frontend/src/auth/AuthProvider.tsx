import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi } from "../api/auth";
import { ALL_PERMISSIONS } from "./permissions";
import { clearAccessToken, getAccessToken, securityEnabled, setAccessToken } from "./authSession";
import type { AuthUser } from "./types";
import { ForcePasswordChange } from "./ForcePasswordChange";

interface AuthContextValue {
  securityEnabled: boolean;
  user: AuthUser | null;
  loading: boolean;
  authenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (permission?: string | string[]) => boolean;
  refreshUser: () => Promise<void>;
}

const demoUser: AuthUser = {
  id: "demo",
  username: "demo",
  displayName: "Người dùng demo",
  roles: ["DEMO"],
  functions: ALL_PERMISSIONS,
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(securityEnabled ? null : demoUser);
  const [loading, setLoading] = useState(securityEnabled);

  const refreshUser = useCallback(async () => {
    if (!securityEnabled) {
      setUser(demoUser);
      return;
    }
    if (!getAccessToken()) {
      const refreshed = await authApi.refresh();
      setAccessToken(refreshed.accessToken);
    }
    const currentUser = await authApi.me();
    setUser({ ...currentUser, roles: currentUser.roles ?? [], functions: currentUser.functions ?? [] });
  }, []);

  useEffect(() => {
    if (!securityEnabled) {
      setLoading(false);
      return;
    }
    refreshUser().catch(() => {
      clearAccessToken();
      setUser(null);
    }).finally(() => setLoading(false));
  }, [refreshUser]);

  useEffect(() => {
    const handleUnauthorized = () => {
      clearAccessToken();
      setUser(null);
      setLoading(false);
    };
    window.addEventListener("auth:unauthorized", handleUnauthorized);
    return () => window.removeEventListener("auth:unauthorized", handleUnauthorized);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const result = await authApi.login(username, password);
    setAccessToken(result.accessToken);
    if (result.user) {
      setUser({ ...result.user, roles: result.user.roles ?? [], functions: result.user.functions ?? [] });
    } else {
      await refreshUser();
    }
  }, [refreshUser]);

  const logout = useCallback(async () => {
    try {
      if (securityEnabled && getAccessToken()) await authApi.logout();
    } finally {
      clearAccessToken();
      setUser(securityEnabled ? null : demoUser);
    }
  }, []);

  const hasPermission = useCallback((permission?: string | string[]) => {
    if (!permission || !securityEnabled) return true;
    const required = Array.isArray(permission) ? permission : [permission];
    return required.some((code) => user?.functions.includes(code));
  }, [user]);

  const value = useMemo<AuthContextValue>(() => ({
    securityEnabled,
    user,
    loading,
    authenticated: !securityEnabled || Boolean(user),
    login,
    logout,
    hasPermission,
    refreshUser,
  }), [user, loading, login, logout, hasPermission, refreshUser]);

  return <AuthContext.Provider value={value}>{children}<ForcePasswordChange user={user} onChanged={() => { clearAccessToken(); setUser(null); }} /></AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth phải được sử dụng bên trong AuthProvider");
  return context;
}
