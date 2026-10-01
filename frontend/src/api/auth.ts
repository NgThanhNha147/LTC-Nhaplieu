import { apiClient, toPage, unwrap } from "./client";
import type { AuditEvent, AuthUser, FunctionDefinition, LoginResponse, ManagedRole, ManagedUser } from "../auth/types";
import type { PageResult } from "../types";

export const authApi = {
  async login(username: string, password: string): Promise<LoginResponse> {
    const response = await apiClient.post("/auth/login", { username, password });
    return unwrap<LoginResponse>(response.data);
  },
  async me(): Promise<AuthUser> {
    const response = await apiClient.get("/auth/me");
    return unwrap<AuthUser>(response.data);
  },
  async refresh(): Promise<LoginResponse> {
    const response = await apiClient.post("/auth/refresh");
    return unwrap<LoginResponse>(response.data);
  },
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await apiClient.post("/auth/change-password", { currentPassword, newPassword });
  },
  async logout(): Promise<void> {
    await apiClient.post("/auth/logout");
  },
};

export const auditApi = {
  async events(params?: { actor?: string; action?: string; eventType?: string; from?: string; to?: string; page?: number; size?: number }): Promise<PageResult<AuditEvent>> {
    const response = await apiClient.get("/audit/events", { params });
    return toPage<AuditEvent>(response.data);
  },
};

export const securityAdminApi = {
  async users(params?: { keyword?: string; page?: number; size?: number }): Promise<PageResult<ManagedUser>> {
    const response = await apiClient.get("/admin/users", { params });
    return toPage<ManagedUser>(response.data);
  },
  async createUser(payload: { username: string; password: string; displayName: string; email?: string; roleIds: string[]; mustChangePassword: boolean }): Promise<ManagedUser> {
    const response = await apiClient.post("/admin/users", payload);
    return unwrap<ManagedUser>(response.data);
  },
  async updateUser(id: string, payload: { displayName: string; email?: string; status: string; newPassword?: string; mustChangePassword: boolean; rowVersion: number }): Promise<ManagedUser> {
    const response = await apiClient.put(`/admin/users/${id}`, payload);
    return unwrap<ManagedUser>(response.data);
  },
  async assignUserRoles(id: string, ids: string[]): Promise<void> {
    await apiClient.put(`/admin/users/${id}/roles`, { ids });
  },
  async roles(): Promise<ManagedRole[]> {
    const response = await apiClient.get("/admin/roles");
    return unwrap<ManagedRole[]>(response.data);
  },
  async createRole(payload: { code: string; name: string; description?: string; active: boolean; rowVersion: number }): Promise<ManagedRole> {
    const response = await apiClient.post("/admin/roles", payload);
    return unwrap<ManagedRole>(response.data);
  },
  async updateRole(id: string, payload: { code: string; name: string; description?: string; active: boolean; rowVersion: number }): Promise<ManagedRole> {
    const response = await apiClient.put(`/admin/roles/${id}`, payload);
    return unwrap<ManagedRole>(response.data);
  },
  async assignRoleFunctions(id: string, ids: string[]): Promise<void> {
    await apiClient.put(`/admin/roles/${id}/functions`, { ids });
  },
  async functions(): Promise<FunctionDefinition[]> {
    const response = await apiClient.get("/admin/functions");
    return unwrap<FunctionDefinition[]>(response.data);
  },
};
