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
    const response = await fetch(`${apiBaseUrl}/documents`, { method: "POST", body });
    if (!response.ok) {
      const error = await response.json().catch(() => undefined) as { message?: string } | undefined;
      throw new Error(error?.message || "Không thể tải tài liệu lên");
    }
    return response.json() as Promise<UploadedDocument>;
  },
  contentUrl(id: string): string {
    return `${apiBaseUrl}/documents/${encodeURIComponent(id)}/content`;
  },
  idFromContentUrl(url: string): string | undefined {
    const match = url.match(/\/documents\/([^/]+)\/content(?:$|[?#])/);
    return match?.[1] ? decodeURIComponent(match[1]) : undefined;
  },
  async ocrStatus(id: string): Promise<OcrStatus> {
    const response = await fetch(`${apiBaseUrl}/documents/${encodeURIComponent(id)}/ocr`, { credentials: "same-origin" });
    if (!response.ok) throw new Error("Chưa có trạng thái OCR cho tài liệu này.");
    const value = await response.json() as OcrStatus;
    return { ...value, ocrContentUrl: value.ocrContentUrl || value.ocrPdfUrl };
  },
  async startOcr(id: string): Promise<OcrStatus> {
    const response = await fetch(`${apiBaseUrl}/documents/${encodeURIComponent(id)}/ocr`, { method: "POST", credentials: "same-origin" });
    if (!response.ok) {
      const error = await response.json().catch(() => undefined) as { message?: string } | undefined;
      throw new Error(error?.message || "Không thể nhận dạng văn bản");
    }
    const value = await response.json() as OcrStatus;
    return { ...value, ocrContentUrl: value.ocrContentUrl || value.ocrPdfUrl };
  },
};
