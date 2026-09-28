import { apiClient, toPage, unwrap } from "./client";
import type { DynamicRecord, LookupOption, PageResult, SearchRequest } from "../types";

interface RecordPayload {
  sourceDocumentId?: string;
  status?: string;
  rowVersion?: number;
  data: Record<string, unknown>;
}

export const recordApi = {
  async search(code: string, request: SearchRequest): Promise<PageResult<DynamicRecord>> {
    const { data } = await apiClient.post(`/templates/${encodeURIComponent(code)}/records/search`, request);
    return toPage<DynamicRecord>(data);
  },
  async get(code: string, id: string): Promise<DynamicRecord> {
    const { data } = await apiClient.get(`/templates/${encodeURIComponent(code)}/records/${id}`);
    const record = unwrap<DynamicRecord>(data);
    // Some APIs flatten dynamic fields next to system columns; normalize that shape here.
    if (!record.data) {
      const { id: recordId, sourceDocumentId, sourceDocumentUrl, recordStatus, rowVersion, createdAt, updatedAt, ...values } = record;
      return { id: recordId, sourceDocumentId, sourceDocumentUrl, recordStatus, rowVersion, createdAt, updatedAt, data: values };
    }
    return record;
  },
  async create(code: string, payload: RecordPayload): Promise<DynamicRecord> {
    const { data } = await apiClient.post(`/templates/${encodeURIComponent(code)}/records`, payload);
    return unwrap(data);
  },
  async update(code: string, id: string, payload: RecordPayload): Promise<DynamicRecord> {
    const { data } = await apiClient.put(`/templates/${encodeURIComponent(code)}/records/${id}`, payload);
    return unwrap(data);
  },
  async remove(code: string, id: string, rowVersion: number): Promise<void> {
    await apiClient.delete(`/templates/${encodeURIComponent(code)}/records/${id}`, {
      params: { rowVersion },
    });
  },
  async export(code: string, request: SearchRequest): Promise<void> {
    const response = await apiClient.post(`/templates/${encodeURIComponent(code)}/records/export`, request, {
      responseType: "blob",
    });
    const url = URL.createObjectURL(response.data);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${code}-${new Date().toISOString().slice(0, 10)}.xlsx`;
    anchor.click();
    URL.revokeObjectURL(url);
  },
  async lookup(code: string, query = "", page = 0, size = 30): Promise<LookupOption[]> {
    const { data } = await apiClient.get(`/lookups/${encodeURIComponent(code)}/options`, {
      params: { q: query || undefined, page, size },
    });
    const result = unwrap<LookupOption[] | { items?: LookupOption[]; content?: LookupOption[] }>(data);
    return Array.isArray(result) ? result : result.items ?? result.content ?? [];
  },
};
