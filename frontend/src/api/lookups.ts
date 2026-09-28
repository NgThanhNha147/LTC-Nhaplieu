import { apiClient, unwrap } from "./client";
import type { LookupOption, LookupSource, LookupSourcePayload } from "../types";

export const lookupApi = {
  async list(): Promise<LookupSource[]> {
    const { data } = await apiClient.get("/lookups");
    const result = unwrap<LookupSource[] | { items?: LookupSource[]; content?: LookupSource[] }>(data);
    return Array.isArray(result) ? result : result.items ?? result.content ?? [];
  },
  async create(payload: LookupSourcePayload): Promise<LookupSource> {
    const { data } = await apiClient.post("/lookups", payload);
    return unwrap(data);
  },
  async update(id: string, payload: LookupSourcePayload): Promise<LookupSource> {
    const { data } = await apiClient.put(`/lookups/${id}`, payload);
    return unwrap(data);
  },
  async remove(id: string): Promise<void> {
    await apiClient.delete(`/lookups/${id}`);
  },
  async options(code: string, query = "", size = 8): Promise<LookupOption[]> {
    const { data } = await apiClient.get(`/lookups/${encodeURIComponent(code)}/options`, {
      params: { q: query || undefined, page: 0, size },
    });
    const result = unwrap<LookupOption[] | { items?: LookupOption[]; content?: LookupOption[] }>(data);
    return Array.isArray(result) ? result : result.items ?? result.content ?? [];
  },
};
