import { apiClient, toPage, unwrap } from "./client";
import type {
  FieldDefinition,
  FieldGroup,
  FormMetadata,
  LookupSource,
  MigrationPlan,
  PageResult,
  TemplateSummary,
  TemplateVersion,
  FormRule,
  StorageCheck,
  ValidationResult,
} from "../types";

export const templateApi = {
  async list(page = 0, size = 50): Promise<PageResult<TemplateSummary>> {
    const { data } = await apiClient.get("/templates", { params: { page, size } });
    return toPage<TemplateSummary>(data);
  },
  async get(id: string): Promise<TemplateSummary> {
    const { data } = await apiClient.get(`/templates/${id}`);
    return unwrap(data);
  },
  async create(payload: Pick<TemplateSummary, "code" | "name" | "description">): Promise<TemplateSummary> {
    const { data } = await apiClient.post("/templates", payload);
    return unwrap(data);
  },
  async update(id: string, payload: Partial<TemplateSummary>): Promise<TemplateSummary> {
    const { data } = await apiClient.put(`/templates/${id}`, payload);
    return unwrap(data);
  },
  async remove(id: string): Promise<void> {
    await apiClient.delete(`/templates/${id}`);
  },
  async versions(templateId: string): Promise<TemplateVersion[]> {
    const { data } = await apiClient.get(`/templates/${templateId}/versions`);
    const unwrapped = unwrap<TemplateVersion[] | { content?: TemplateVersion[]; items?: TemplateVersion[] }>(data);
    return Array.isArray(unwrapped) ? unwrapped : unwrapped.items ?? unwrapped.content ?? [];
  },
  async createVersion(templateId: string): Promise<TemplateVersion> {
    const { data } = await apiClient.post(`/templates/${templateId}/versions`);
    return unwrap(data);
  },
  async groups(versionId: string): Promise<FieldGroup[]> {
    const { data } = await apiClient.get(`/template-versions/${versionId}/groups`);
    const result = unwrap<FieldGroup[] | { items?: FieldGroup[]; content?: FieldGroup[] }>(data);
    return Array.isArray(result) ? result : result.items ?? result.content ?? [];
  },
  async addGroup(versionId: string, payload: Partial<FieldGroup>): Promise<FieldGroup> {
    const { data } = await apiClient.post(`/template-versions/${versionId}/groups`, payload);
    return unwrap(data);
  },
  async updateGroup(groupId: string, payload: Partial<FieldGroup>): Promise<FieldGroup> {
    const { data } = await apiClient.put(`/groups/${groupId}`, payload);
    return unwrap(data);
  },
  async removeGroup(groupId: string): Promise<void> {
    await apiClient.delete(`/groups/${groupId}`);
  },
  async fields(versionId: string): Promise<FieldDefinition[]> {
    const { data } = await apiClient.get(`/template-versions/${versionId}/fields`);
    const result = unwrap<FieldDefinition[] | { items?: FieldDefinition[]; content?: FieldDefinition[] }>(data);
    return Array.isArray(result) ? result : result.items ?? result.content ?? [];
  },
  async addField(versionId: string, payload: Partial<FieldDefinition>): Promise<FieldDefinition> {
    const { data } = await apiClient.post(`/template-versions/${versionId}/fields`, payload);
    return unwrap(data);
  },
  async updateField(fieldId: string, payload: Partial<FieldDefinition>): Promise<FieldDefinition> {
    const { data } = await apiClient.put(`/fields/${fieldId}`, payload);
    return unwrap(data);
  },
  async removeField(fieldId: string): Promise<void> {
    await apiClient.delete(`/fields/${fieldId}`);
  },
  async validate(versionId: string): Promise<ValidationResult> {
    const { data } = await apiClient.post(`/template-versions/${versionId}/validate`);
    return unwrap(data);
  },
  async migrationPlan(versionId: string): Promise<MigrationPlan> {
    const { data } = await apiClient.get(`/template-versions/${versionId}/migration-plan`);
    return unwrap(data);
  },
  async ddl(versionId: string): Promise<string> {
    const { data } = await apiClient.get(`/template-versions/${versionId}/ddl-preview`, {
      headers: { Accept: "text/plain, application/json" },
    });
    const result = unwrap<string | { ddl: string }>(data);
    return typeof result === "string" ? result : result.ddl;
  },
  async generate(versionId: string): Promise<TemplateVersion> {
    const { data } = await apiClient.post(`/template-versions/${versionId}/generate`);
    return unwrap(data);
  },
  async publish(versionId: string): Promise<TemplateVersion> {
    const { data } = await apiClient.post(`/template-versions/${versionId}/publish`);
    return unwrap(data);
  },
  async storageCheck(versionId: string): Promise<StorageCheck> {
    const { data } = await apiClient.get(`/template-versions/${versionId}/storage-check`);
    return unwrap(data);
  },
  async cancelGenerated(versionId: string): Promise<TemplateVersion> {
    const { data } = await apiClient.post(`/template-versions/${versionId}/cancel-generated`);
    return unwrap(data);
  },
  async purgeArchived(versionId: string, reason?: string): Promise<TemplateVersion> {
    const { data } = await apiClient.delete(`/template-versions/${versionId}/storage`, { params: { reason } });
    return unwrap(data);
  },
  async formRules(versionId: string): Promise<FormRule[]> {
    const { data } = await apiClient.get(`/template-versions/${versionId}/form-rules`);
    return unwrap(data);
  },
  async addFormRule(versionId: string, payload: Omit<FormRule, "id">): Promise<FormRule> {
    const { data } = await apiClient.post(`/template-versions/${versionId}/form-rules`, payload);
    return unwrap(data);
  },
  async form(code: string): Promise<FormMetadata> {
    const { data } = await apiClient.get(`/forms/${encodeURIComponent(code)}`);
    return unwrap(data);
  },
  async formVersion(code: string, versionNo: number): Promise<FormMetadata> {
    const { data } = await apiClient.get(`/forms/${encodeURIComponent(code)}/versions/${versionNo}`);
    return unwrap(data);
  },
  async lookupSources(): Promise<LookupSource[]> {
    try {
      const { data } = await apiClient.get("/lookup-sources");
      const result = unwrap<LookupSource[] | { items?: LookupSource[]; content?: LookupSource[] }>(data);
      return Array.isArray(result) ? result : result.items ?? result.content ?? [];
    } catch {
      return [];
    }
  },
};
