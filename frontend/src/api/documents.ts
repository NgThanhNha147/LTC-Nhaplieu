import { apiClient, unwrap } from "./client";

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(/\/$/, "");

export interface UploadedDocument {
  id: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
}

export type OcrStatus = {
  status: "NOT_STARTED" | "PROCESSING" | "COMPLETED" | "FAILED" | string;
  text?: string;
  pageCount?: number;
  message?: string;
  error?: string;
  ocrContentUrl?: string;
  ocrPdfUrl?: string;
};

export const documentApi = {
  async upload(file: File): Promise<UploadedDocument> {
    const body = new FormData();
    body.append("file", file);
    const response = await apiClient.post("/documents", body, { headers: { "Content-Type": "multipart/form-data" } });
    return unwrap<UploadedDocument>(response.data);
  },
  contentUrl(id: string): string {
    return `${apiBaseUrl}/documents/${encodeURIComponent(id)}/content`;
  },
  async contentObjectUrl(id: string): Promise<{ url: string; contentType: string }> {
    const response = await apiClient.get(`/documents/${encodeURIComponent(id)}/content`, { responseType: "blob" });
    const contentType = String(response.headers["content-type"] || response.data.type || "application/octet-stream");
    return { url: URL.createObjectURL(response.data), contentType };
  },
  idFromContentUrl(url: string): string | undefined {
    const match = url.match(/\/documents\/([^/]+)\/content(?:$|[?#])/);
    return match?.[1] ? decodeURIComponent(match[1]) : undefined;
  },
  async ocrStatus(id: string): Promise<OcrStatus> {
    const response = await apiClient.get(`/documents/${encodeURIComponent(id)}/ocr`);
    const value = unwrap<OcrStatus>(response.data);
    return { ...value, ocrContentUrl: value.ocrContentUrl || value.ocrPdfUrl };
  },
  async startOcr(id: string): Promise<OcrStatus> {
    const response = await apiClient.post(`/documents/${encodeURIComponent(id)}/ocr`);
    const value = unwrap<OcrStatus>(response.data);
    return { ...value, ocrContentUrl: value.ocrContentUrl || value.ocrPdfUrl };
  },
};
