export type TemplateStatus = "DRAFT" | "VALIDATED" | "GENERATED" | "PUBLISHED" | "ARCHIVED";

export type FieldDataType =
  | "STRING"
  | "TEXTAREA"
  | "INTEGER"
  | "DECIMAL"
  | "DATE"
  | "DATETIME"
  | "BOOLEAN"
  | "LIST"
  | "EMAIL"
  | "PHONE";

export type ComponentType =
  | "TEXT"
  | "TEXTAREA"
  | "NUMBER"
  | "DATE"
  | "DATETIME"
  | "CHECKBOX"
  | "SWITCH"
  | "SELECT"
  | "AUTOCOMPLETE"
  | "EMAIL"
  | "PHONE";

export interface TemplateSummary {
  id: string;
  code: string;
  name: string;
  description?: string;
  status: TemplateStatus;
  currentVersion?: number;
  currentVersionId?: string;
  updatedAt?: string;
}

export interface TemplateVersion {
  id: string;
  templateId: string;
  versionNo: number;
  status: TemplateStatus;
  physicalSchema?: string;
  physicalTable?: string;
  generatedAt?: string;
  publishedAt?: string;
  storageStatus?: string;
  purgedAt?: string;
  purgedBy?: string;
  purgeReason?: string;
}

export interface ValidationRule {
  id?: string;
  ruleType: string;
  ruleConfig?: Record<string, unknown>;
  errorMessage?: string;
  displayOrder?: number;
}

export interface LookupConfig {
  lookupSourceId?: string;
  lookupCode?: string;
  selectionMode?: "SINGLE" | "MULTIPLE";
  allowClear?: boolean;
  autocomplete?: boolean;
  minSearchLength?: number;
  dependencyConfig?: Record<string, unknown>;
}

export interface FieldGroup {
  id: string;
  code: string;
  label: string;
  description?: string;
  displayOrder: number;
  columnCount?: number;
  collapsible?: boolean;
  defaultCollapsed?: boolean;
  repeatable?: boolean;
  fields?: FieldDefinition[];
}

export interface FieldDefinition {
  id: string;
  groupId: string;
  fieldCode: string;
  columnName?: string;
  label: string;
  dataType: FieldDataType;
  componentType: ComponentType;
  description?: string;
  placeholder?: string;
  required?: boolean;
  uniqueValue?: boolean;
  businessKey?: boolean;
  indexed?: boolean;
  searchable?: boolean;
  sortable?: boolean;
  exportable?: boolean;
  maxLength?: number;
  precision?: number;
  scale?: number;
  defaultValue?: string | number | boolean | null;
  displayOrder: number;
  gridSpan?: number;
  readOnly?: boolean;
  hidden?: boolean;
  helpText?: string;
  lookupSourceId?: string;
  validations?: ValidationRule[];
  lookup?: LookupConfig;
}

export interface FormMetadata {
  templateId?: string;
  templateCode: string;
  templateName: string;
  description?: string;
  version: number;
  versionId?: string;
  groups: FieldGroup[];
  rules?: FormRule[];
}

export interface FormRule {
  id?: string;
  ruleType: "AT_LEAST_ONE_FILLED";
  fieldCodes: string[];
  errorMessage?: string;
  displayOrder?: number;
}

export interface StorageCheck {
  eligible: boolean;
  currentVersion?: number;
  sourceRecordCount: number;
  currentRecordCount: number;
  missingRecordCount: number;
  blockers: string[];
}

export interface ValidationIssue {
  level?: "ERROR" | "WARNING";
  fieldCode?: string;
  code: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
}

export interface MigrationChange {
  changeType: "ADDED" | "MODIFIED" | "REMOVED";
  severity: "SAFE" | "WARNING" | "BLOCKING";
  fieldCode: string;
  label: string;
  message: string;
}

export interface MigrationPlan {
  sourceVersion?: number;
  targetVersion: number;
  sourceTable?: string;
  targetTable: string;
  sourceRecordCount: number;
  compatible: boolean;
  addedFields: number;
  modifiedFields: number;
  removedFields: number;
  changes: MigrationChange[];
  blockers: string[];
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DynamicRecord {
  id: string;
  sourceDocumentId?: string;
  sourceDocumentUrl?: string;
  recordStatus?: string;
  rowVersion?: number;
  createdAt?: string;
  updatedAt?: string;
  data: Record<string, unknown>;
}

export interface SearchFilter {
  field: string;
  operator: string;
  value?: unknown;
  toValue?: unknown;
}

export interface SearchRequest {
  keyword?: string;
  dateField?: "createdAt" | "updatedAt";
  fromDate?: string;
  toDate?: string;
  status?: string;
  hasDocument?: boolean;
  filters: SearchFilter[];
  sort?: Array<{ field: string; direction: "ASC" | "DESC" }>;
  page: number;
  size: number;
}

export interface LookupOption {
  value: string | number;
  label: string;
}

export interface LookupSource {
  id: string;
  code: string;
  name: string;
  sourceType: string;
  sourceSchema: string;
  sourceTable: string;
  valueColumn: string;
  labelColumn: string;
  activeColumn?: string;
  parentColumn?: string;
  sortColumn?: string;
  status: string;
}

export type LookupSourcePayload = Omit<LookupSource, "id">;

export interface ApiErrorBody {
  code?: string;
  message?: string;
  fieldErrors?: Array<{ field: string; code?: string; message: string }>;
}
