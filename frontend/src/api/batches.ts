import { apiClient, toPage, unwrap } from "./client";
import type { IngestionBatch, PageResult, WorkItem, WorkItemSearch } from "../types";

export interface CreateBatchPayload {
  batchName: string;
  templateVersionId: string;
  assignedUserId?: string;
  description?: string;
}

export interface BatchUploadResult {
  batch: IngestionBatch;
  accepted: number;
  duplicates: number;
  rejected: number;
  errors: Array<{ fileName: string; code: string; message: string }>;
}

interface RawBatchUploadResult {
  accepted?: number;
  duplicates?: number;
  rejected?: number;
  errors?: Array<{ fileName: string; code: string; message: string }>;
}

interface RawBatch extends Omit<IngestionBatch, "counters" | "templateVersion" | "assignedDisplayName"> {
  statistics?: Partial<IngestionBatch["counters"]> & { totalFiles?: number };
  counters?: IngestionBatch["counters"];
  templateVersionNo?: number;
  templateVersion?: number;
  assignedUserName?: string;
  assignedDisplayName?: string;
}

interface RawWorkItem extends Partial<WorkItem> {
  documentFileId?: string;
  displayName?: string;
  assignedUserName?: string;
  templateVersionNo?: number;
}

interface RawWorkItemDetail {
  item: RawWorkItem;
  dynamicRecord?: { data?: Record<string, unknown>; rowVersion?: number } & Record<string, unknown>;
}

function normalizeBatch(raw: RawBatch): IngestionBatch {
  const statistics = raw.statistics ?? raw.counters;
  const totalFiles = statistics && "totalFiles" in statistics ? statistics.totalFiles : undefined;
  return {
    ...raw,
    templateVersion: raw.templateVersionNo ?? raw.templateVersion,
    assignedDisplayName: raw.assignedUserName ?? raw.assignedDisplayName,
    counters: {
      total: statistics?.total ?? totalFiles ?? 0,
      unprocessed: statistics?.unprocessed ?? 0,
      draft: statistics?.draft ?? 0,
      pendingApproval: statistics?.pendingApproval ?? 0,
      approved: statistics?.approved ?? 0,
      rejected: statistics?.rejected ?? 0,
      deleted: statistics?.deleted ?? 0,
    },
  } as IngestionBatch;
}

function normalizeWorkItem(raw: RawWorkItem): WorkItem {
  return {
    ...raw,
    id: raw.id ?? "",
    batchId: raw.batchId ?? "",
    batchCode: raw.batchCode ?? "",
    batchName: raw.batchName ?? "",
    templateCode: raw.templateCode ?? "",
    templateName: raw.templateName ?? raw.templateCode ?? "Biểu mẫu",
    templateVersion: raw.templateVersion ?? raw.templateVersionNo,
    sequenceNo: raw.sequenceNo ?? 0,
    documentId: raw.documentId ?? raw.documentFileId,
    documentName: raw.documentName ?? raw.displayName ?? "Tài liệu",
    assignedDisplayName: raw.assignedDisplayName ?? raw.assignedUserName,
    status: raw.status ?? "UNPROCESSED",
  };
}

function normalizeWorkItemResponse(raw: RawWorkItemDetail | RawWorkItem): WorkItem {
  if ("item" in raw) {
    const item = normalizeWorkItem(raw.item);
    const dynamic = raw.dynamicRecord;
    return { ...item, data: dynamic?.data ?? item.data ?? {}, dynamicRowVersion: dynamic?.rowVersion };
  }
  return normalizeWorkItem(raw);
}

export interface BatchSearchParams {
  keyword?: string;
  templateId?: string;
  templateVersionId?: string;
  assignedUserId?: string;
  archived?: boolean;
  page?: number;
  size?: number;
}

export const batchApi = {
  async list(params: BatchSearchParams = {}): Promise<PageResult<IngestionBatch>> {
    const response = await apiClient.get("/batches", { params });
    const page = toPage<RawBatch>(response.data);
    return { ...page, items: page.items.map(normalizeBatch) };
  },
  async get(id: string): Promise<IngestionBatch> {
    const response = await apiClient.get(`/batches/${id}`);
    return normalizeBatch(unwrap<RawBatch>(response.data));
  },
  async create(payload: CreateBatchPayload): Promise<IngestionBatch> {
    const response = await apiClient.post("/batches", payload);
    return normalizeBatch(unwrap<RawBatch>(response.data));
  },
  async assign(id: string, assignedUserId: string, rowVersion: number, reason: string): Promise<IngestionBatch> {
    const response = await apiClient.post(`/batches/${id}/assign`, { assignedUserId, rowVersion, reason });
    return normalizeBatch(unwrap<RawBatch>(response.data));
  },
  async archive(id: string, rowVersion: number): Promise<IngestionBatch> {
    const response = await apiClient.post(`/batches/${id}/archive`, undefined, { params: { rowVersion } });
    return normalizeBatch(unwrap<RawBatch>(response.data));
  },
  async export(id: string, params: { status?: string; fromDate?: string; toDate?: string; format?: "xlsx" | "csv" } = {}): Promise<void> {
    const response = await apiClient.get(`/batches/${id}/export`, {
      params: { format: "xlsx", ...params },
      responseType: "blob",
      timeout: 120_000,
    });
    const disposition = String(response.headers["content-disposition"] ?? "");
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1];
    const filename = encodedName ? decodeURIComponent(encodedName) : plainName ?? `dot-ho-so-${id}.xlsx`;
    const url = URL.createObjectURL(response.data);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  },
  async upload(id: string, files: File[], sourceType: "ZIP" | "FOLDER" | "MULTI_FILE"): Promise<BatchUploadResult> {
    const body = new FormData();
    if (sourceType === "ZIP") {
      body.append("file", files[0]);
    } else {
      body.append("sourceType", sourceType);
      files.forEach((file) => {
        body.append("files", file, file.name);
        body.append("relativePaths", file.webkitRelativePath || file.name);
      });
    }
    const response = await apiClient.post(`/batches/${id}/upload/${sourceType === "ZIP" ? "zip" : "files"}`, body, {
      headers: { "Content-Type": "multipart/form-data" },
      timeout: 10 * 60_000,
    });
    const result = unwrap<RawBatchUploadResult>(response.data);
    return {
      batch: await this.get(id),
      accepted: result.accepted ?? 0,
      duplicates: result.duplicates ?? 0,
      rejected: result.rejected ?? 0,
      errors: result.errors ?? [],
    };
  },
};

export const workItemApi = {
  async list(params: WorkItemSearch = {}): Promise<PageResult<WorkItem>> {
    const response = await apiClient.get("/work-items", {
      params: { ...params, statuses: params.statuses?.join(",") },
    });
    const page = toPage<RawWorkItem>(response.data);
    return { ...page, items: page.items.map(normalizeWorkItem) };
  },
  async get(id: string): Promise<WorkItem> {
    const response = await apiClient.get(`/work-items/${id}`);
    return normalizeWorkItemResponse(unwrap<RawWorkItemDetail | RawWorkItem>(response.data));
  },
  async saveDraft(id: string, payload: { data: Record<string, unknown>; rowVersion?: number; dynamicRowVersion?: number }): Promise<WorkItem> {
    const response = await apiClient.put(`/work-items/${id}/draft`, payload);
    return normalizeWorkItemResponse(unwrap<RawWorkItemDetail | RawWorkItem>(response.data));
  },
  async submit(id: string, payload: { data: Record<string, unknown>; rowVersion?: number; dynamicRowVersion?: number }): Promise<WorkItem> {
    const response = await apiClient.post(`/work-items/${id}/submit`, payload);
    return normalizeWorkItemResponse(unwrap<RawWorkItemDetail | RawWorkItem>(response.data));
  },
  async approve(id: string, rowVersion?: number): Promise<WorkItem> {
    const response = await apiClient.post(`/work-items/${id}/approve`, { rowVersion });
    return normalizeWorkItemResponse(unwrap<RawWorkItemDetail | RawWorkItem>(response.data));
  },
  async reject(id: string, rejectionReason: string, rowVersion?: number): Promise<WorkItem> {
    const response = await apiClient.post(`/work-items/${id}/reject`, { rejectionReason, rowVersion });
    return normalizeWorkItemResponse(unwrap<RawWorkItemDetail | RawWorkItem>(response.data));
  },
  async remove(id: string, rowVersion?: number): Promise<void> {
    await apiClient.delete(`/work-items/${id}`, { params: { rowVersion } });
  },
  async restore(id: string, rowVersion: number, reason?: string): Promise<WorkItem> {
    const response = await apiClient.post(`/work-items/${id}/restore`, { rowVersion, reason });
    return normalizeWorkItemResponse(unwrap<RawWorkItemDetail | RawWorkItem>(response.data));
  },
  async previous(id: string): Promise<WorkItem | null> {
    const response = await apiClient.get(`/work-items/${id}/previous`);
    const raw = unwrap<RawWorkItem | null>(response.data);
    return raw ? normalizeWorkItem(raw) : null;
  },
  async next(id: string): Promise<WorkItem | null> {
    const response = await apiClient.get(`/work-items/${id}/next`);
    const raw = unwrap<RawWorkItem | null>(response.data);
    return raw ? normalizeWorkItem(raw) : null;
  },
  async reopen(id: string, reason: string, rowVersion?: number): Promise<WorkItem> {
    const response = await apiClient.post(`/work-items/${id}/reopen`, { reason, rowVersion });
    return normalizeWorkItemResponse(unwrap<RawWorkItemDetail | RawWorkItem>(response.data));
  },
};
