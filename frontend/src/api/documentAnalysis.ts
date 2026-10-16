import { apiClient, unwrap } from "./client";
import type {
  DocumentAnalysisResult,
  PromoteAnalysisResult,
  SuggestedDocumentForm,
} from "../types";

export const documentAnalysisApi = {
  async analyze(documentId: string): Promise<DocumentAnalysisResult> {
    const { data } = await apiClient.post(`/documents/${encodeURIComponent(documentId)}/analyses`, {}, { timeout: 210_000 });
    return unwrap(data);
  },
  async get(id: string): Promise<DocumentAnalysisResult> {
    const { data } = await apiClient.get(`/document-analyses/${encodeURIComponent(id)}`);
    return unwrap(data);
  },
  async recent(): Promise<DocumentAnalysisResult[]> {
    const { data } = await apiClient.get("/document-analyses");
    return unwrap(data);
  },
  async review(id: string, form: SuggestedDocumentForm, rowVersion: number): Promise<DocumentAnalysisResult> {
    const { data } = await apiClient.put(`/document-analyses/${encodeURIComponent(id)}/review`, { form, rowVersion });
    return unwrap(data);
  },
  async promote(id: string, payload: { templateCode: string; templateName: string; description?: string; rowVersion: number }): Promise<PromoteAnalysisResult> {
    const { data } = await apiClient.post(`/document-analyses/${encodeURIComponent(id)}/promote`, payload);
    return unwrap(data);
  },
};
