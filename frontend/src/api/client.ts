import axios, { AxiosError } from "axios";
import type { ApiErrorBody, PageResult } from "../types";

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  timeout: 30_000,
  headers: { "Content-Type": "application/json" },
});

// Keeps screens independent from whether Spring returns a direct body or { data: ... }.
export function unwrap<T>(payload: T | { data: T }): T {
  if (
    payload &&
    typeof payload === "object" &&
    "data" in payload &&
    !("id" in payload) &&
    !("templateCode" in payload)
  ) {
    return (payload as { data: T }).data;
  }
  return payload as T;
}

export function toPage<T>(payload: unknown): PageResult<T> {
  const normalized = unwrap(payload as Record<string, unknown> | T[]);
  if (Array.isArray(normalized)) {
    return { items: normalized, page: 0, size: normalized.length, totalElements: normalized.length, totalPages: normalized.length ? 1 : 0 };
  }
  const raw = normalized as Record<string, unknown>;
  const items = (raw.items ?? raw.content ?? []) as T[];
  const page = Number(raw.page ?? raw.number ?? 0);
  const size = Number(raw.size ?? items.length ?? 20);
  const totalElements = Number(raw.totalElements ?? raw.total ?? items.length);
  return {
    items,
    page,
    size,
    totalElements,
    totalPages: Number(raw.totalPages ?? Math.ceil(totalElements / Math.max(size, 1))),
  };
}

export function getErrorMessage(error: unknown): string {
  const axiosError = error as AxiosError<ApiErrorBody>;
  const status = axiosError.response?.status;
  const body = axiosError.response?.data;
  const fieldError = body?.fieldErrors?.find((item) => item.message)?.message;
  if (status === 403) return "Bạn không có quyền thực hiện thao tác này. Hãy kiểm tra tài khoản đang sử dụng.";
  if (!axiosError.response) return "Không thể kết nối đến hệ thống. Hãy kiểm tra mạng và thử lại.";
  if (fieldError) return `${body?.message || "Dữ liệu chưa hợp lệ"}: ${fieldError}`;
  return body?.message || axiosError.message || "Đã có lỗi xảy ra. Hãy thử lại.";
}
