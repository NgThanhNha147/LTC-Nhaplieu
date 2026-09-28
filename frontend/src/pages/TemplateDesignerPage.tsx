import {
  ArrowRightOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CodeOutlined,
  CopyOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  MoreOutlined,
  PlusOutlined,
  RocketOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Collapse,
  Divider,
  Dropdown,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Popconfirm,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Switch,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import { useMemo, useState } from "react";
import { useForm as useRuntimeForm } from "react-hook-form";
import { useNavigate, useParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import { templateApi } from "../api/templates";
import { PageError, PageLoading } from "../components/ApiState";
import { DynamicFormRenderer } from "../components/DynamicFormRenderer";
import type { ComponentType, FieldDataType, FieldDefinition, FieldGroup, FormMetadata, MigrationPlan, TemplateStatus, ValidationIssue, ValidationResult } from "../types";

const dataTypes: Array<{ value: FieldDataType; label: string }> = [
  { value: "STRING", label: "Chuỗi ngắn" }, { value: "TEXTAREA", label: "Văn bản dài" },
  { value: "INTEGER", label: "Số nguyên" }, { value: "DECIMAL", label: "Số thập phân" },
  { value: "DATE", label: "Ngày" }, { value: "DATETIME", label: "Ngày giờ" },
  { value: "BOOLEAN", label: "Đúng / Sai" }, { value: "LIST", label: "Danh sách" },
  { value: "EMAIL", label: "Email" }, { value: "PHONE", label: "Số điện thoại" },
];

const componentByType: Record<FieldDataType, ComponentType> = {
  STRING: "TEXT", TEXTAREA: "TEXTAREA", INTEGER: "NUMBER", DECIMAL: "NUMBER", DATE: "DATE",
  DATETIME: "DATETIME", BOOLEAN: "CHECKBOX", LIST: "SELECT", EMAIL: "EMAIL", PHONE: "PHONE",
};

const lifecycleLabels: Record<TemplateStatus, string> = {
  DRAFT: "Đang cấu hình",
  VALIDATED: "Đã kiểm tra",
  GENERATED: "Đã tạo bảng",
  PUBLISHED: "Đang sử dụng",
  ARCHIVED: "Phiên bản cũ",
};
const dataTypeLabels = Object.fromEntries(dataTypes.map((item) => [item.value, item.label])) as Record<FieldDataType, string>;
const componentOptions: Array<{ value: ComponentType; label: string }> = [
  { value: "TEXT", label: "Ô nhập một dòng" },
  { value: "TEXTAREA", label: "Ô nhập nhiều dòng" },
  { value: "NUMBER", label: "Ô nhập số" },
  { value: "DATE", label: "Chọn ngày" },
  { value: "DATETIME", label: "Chọn ngày và giờ" },
  { value: "CHECKBOX", label: "Ô đánh dấu" },
  { value: "SWITCH", label: "Nút bật / tắt" },
  { value: "SELECT", label: "Danh sách lựa chọn" },
  { value: "AUTOCOMPLETE", label: "Danh sách có tìm kiếm" },
  { value: "EMAIL", label: "Ô nhập email" },
  { value: "PHONE", label: "Ô nhập số điện thoại" },
];
const validationRuleOptions = [
  { value: "MIN_LENGTH", label: "Độ dài tối thiểu" },
  { value: "MAX_LENGTH", label: "Độ dài tối đa" },
  { value: "MIN_VALUE", label: "Giá trị tối thiểu" },
  { value: "MAX_VALUE", label: "Giá trị tối đa" },
  { value: "REGEX", label: "Định dạng tùy chỉnh" },
  { value: "EMAIL", label: "Định dạng email" },
  { value: "DATE_RANGE", label: "Khoảng ngày hợp lệ" },
  { value: "DECIMAL_SCALE", label: "Số chữ số thập phân" },
];
const businessKeyTypes: FieldDataType[] = ["STRING", "INTEGER", "EMAIL", "PHONE"];
const validationRulesByType: Partial<Record<FieldDataType, string[]>> = {
  STRING: ["MIN_LENGTH", "MAX_LENGTH", "REGEX", "EMAIL"],
  TEXTAREA: ["MIN_LENGTH", "MAX_LENGTH", "REGEX"],
  EMAIL: ["MIN_LENGTH", "MAX_LENGTH", "REGEX", "EMAIL"],
  PHONE: ["MIN_LENGTH", "MAX_LENGTH", "REGEX"],
  LIST: ["MIN_LENGTH", "MAX_LENGTH", "REGEX"],
  INTEGER: ["MIN_VALUE", "MAX_VALUE"],
  DECIMAL: ["MIN_VALUE", "MAX_VALUE", "DECIMAL_SCALE"],
  DATE: ["DATE_RANGE"],
};
const validationIssueMessages: Record<string, string> = {
  NO_FIELDS: "Bảng chưa có trường dữ liệu. Hãy tạo ít nhất một nhóm trường, sau đó thêm trường vào nhóm.",
  SYSTEM_COLUMN: "Tên cột đang trùng với cột hệ thống. Mở trường này, vào tab Nâng cao và đổi Tên cột lưu trữ.",
  MISSING_MAX_LENGTH: "Trường dạng văn bản cần có số ký tự tối đa. Mở trường và nhập Số ký tự tối đa.",
  INVALID_DECIMAL: "Trường số thập phân cần Tổng số chữ số lớn hơn Số chữ số sau dấu phẩy, ví dụ 18 và 2.",
  MISSING_LOOKUP: "Trường danh sách chưa có nguồn dữ liệu. Mở trường và chọn một Danh mục dùng chung.",
  UNUSED_LOOKUP: "Trường không còn là dạng danh sách nhưng vẫn còn cấu hình danh mục. Hãy lưu lại trường để hệ thống loại bỏ cấu hình cũ.",
  SEARCH_WITHOUT_INDEX: "Trường được phép tìm kiếm hoặc sắp xếp nhưng chưa có chỉ mục. Bật Tạo chỉ mục dữ liệu để tra cứu nhanh hơn.",
  INCOMPATIBLE_COMPONENT: "Kiểu ô nhập không phù hợp với loại dữ liệu. Chọn lại Loại dữ liệu để hệ thống tự đặt kiểu hiển thị phù hợp.",
  INVALID_RULE_CONFIG: "Điều kiện kiểm tra còn thiếu giá trị. Mở trường, vào tab Điều kiện nhập và điền đủ giới hạn hoặc định dạng.",
  INVALID_REGEX: "Mẫu kiểm tra ký tự chưa đúng cú pháp. Mở trường, vào tab Điều kiện nhập và sửa biểu thức kiểm tra.",
  BUSINESS_KEY_MISSING: "Chưa chọn mã định danh chính. Hãy chọn trường dùng để phân biệt từng hồ sơ, ví dụ mã hồ sơ hoặc số seri.",
  BUSINESS_KEY_MULTIPLE: "Biểu mẫu chỉ được có một mã định danh chính.",
  BUSINESS_KEY_INVALID_TYPE: "Loại dữ liệu này không phù hợp làm mã định danh. Nên dùng chuỗi ngắn, số nguyên hoặc UUID.",
  BUSINESS_KEY_INCONSISTENT: "Mã định danh phải bắt buộc, không trùng, cho phép tìm kiếm và có chỉ mục. Hệ thống có thể sửa tự động.",
  DUPLICATE_FIELD_CODE: "Mã kỹ thuật đang trùng với trường khác.",
  DUPLICATE_COLUMN: "Tên cột lưu trữ đang trùng với trường khác.",
  INVALID_DEFAULT: "Giá trị điền sẵn không đúng với loại dữ liệu đã chọn.",
  REQUIRED_NOT_EDITABLE: "Trường bắt buộc đang bị ẩn hoặc chỉ được xem nhưng chưa có giá trị điền sẵn.",
  LOOKUP_INACTIVE: "Danh mục được chọn đang tạm ngừng hoạt động. Hãy chọn danh mục khác hoặc kích hoạt lại danh mục.",
  RULE_TYPE_MISMATCH: "Điều kiện kiểm tra không phù hợp với loại dữ liệu của trường.",
  INVALID_RULE_RANGE: "Giới hạn tối thiểu đang lớn hơn giới hạn tối đa.",
};

const automaticFixCodes = new Set([
  "SYSTEM_COLUMN", "MISSING_MAX_LENGTH", "INVALID_DECIMAL", "UNUSED_LOOKUP",
  "SEARCH_WITHOUT_INDEX", "INCOMPATIBLE_COMPONENT", "BUSINESS_KEY_INCONSISTENT",
]);

function issueTab(code: string): string {
  if (["SYSTEM_COLUMN", "DUPLICATE_FIELD_CODE", "DUPLICATE_COLUMN", "SEARCH_WITHOUT_INDEX"].includes(code)) return "advanced";
  if (["INVALID_RULE_CONFIG", "INVALID_REGEX", "INVALID_RULE_RANGE", "RULE_TYPE_MISMATCH"].includes(code)) return "rules";
  return "basic";
}

function toFieldCode(label: string): string {
  const words = label.normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[đĐ]/g, "d")
    .replace(/[^A-Za-z0-9]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  const value = words.map((word, index) => {
    const normalized = word.toLowerCase();
    return index === 0 ? normalized : normalized.charAt(0).toUpperCase() + normalized.slice(1);
  }).join("");
  const safe = /^[a-z]/.test(value) ? value : `field${value ? value.charAt(0).toUpperCase() + value.slice(1) : "Value"}`;
  return (safe.length < 2 ? `${safe}Field` : safe).slice(0, 100);
}

function toGroupCode(label: string): string {
  return toFieldCode(label).replace(/([a-z0-9])([A-Z])/g, "$1_$2").toUpperCase();
}

function toColumnName(value: string): string {
  const normalized = value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[đĐ]/g, "d")
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  const safe = /^[a-z]/.test(normalized) ? normalized : `field_${normalized || "value"}`;
  return (safe === "id" ? "business_id" : safe).slice(0, 63).replace(/_+$/g, "");
}

interface ValidationFormValue {
  ruleType: string;
  value?: string;
  min?: string;
  max?: string;
  scale?: number;
  errorMessage?: string;
}

interface FieldFormValue extends Omit<Partial<FieldDefinition>, "validations"> {
  validations?: ValidationFormValue[];
  lookupCode?: string;
  autocomplete?: boolean;
  businessKey?: boolean;
}

function suggestedValidations(type: FieldDataType): ValidationFormValue[] {
  if (type === "EMAIL") return [{ ruleType: "EMAIL", errorMessage: "Email không đúng định dạng" }];
  if (type === "PHONE") return [{ ruleType: "REGEX", value: "^[0-9+() .-]{8,20}$", errorMessage: "Số điện thoại không đúng định dạng" }];
  return [];
}

function automaticRuleDescriptions(value: FieldFormValue): string[] {
  const rules: string[] = [];
  if (value.required) rules.push("Bắt buộc nhập khi hoàn tất hồ sơ");
  if (value.businessKey) rules.push("Khóa định danh: bắt buộc, không trùng và tự tạo chỉ mục");
  switch (value.dataType) {
    case "STRING": rules.push(`Chỉ nhận văn bản, tối đa ${value.maxLength ?? 255} ký tự`); break;
    case "TEXTAREA": rules.push(value.maxLength ? `Văn bản dài, tối đa ${value.maxLength} ký tự` : "Nhận văn bản dài"); break;
    case "INTEGER": rules.push("Chỉ nhận số nguyên"); break;
    case "DECIMAL": rules.push(`Chỉ nhận số, tối đa ${value.precision ?? 18} chữ số và ${value.scale ?? 2} số lẻ`); break;
    case "DATE": rules.push("Chỉ nhận ngày hợp lệ"); break;
    case "DATETIME": rules.push("Chỉ nhận ngày và giờ hợp lệ"); break;
    case "BOOLEAN": rules.push("Chỉ nhận Có hoặc Không"); break;
    case "LIST": rules.push("Giá trị phải tồn tại trong danh mục đã chọn"); break;
    case "EMAIL": rules.push(`Đúng định dạng email, tối đa ${value.maxLength ?? 255} ký tự`); break;
    case "PHONE": rules.push(`Đúng định dạng số điện thoại, tối đa ${value.maxLength ?? 20} ký tự`); break;
  }
  if (value.uniqueValue && !value.businessKey) rules.push("Không được trùng với bản ghi khác");
  return rules;
}

function ruleConfig(rule: ValidationFormValue): Record<string, unknown> {
  if (rule.ruleType === "REGEX") return rule.value ? { pattern: rule.value } : {};
  if (rule.ruleType === "DATE_RANGE") return { min: rule.min || undefined, max: rule.max || undefined };
  if (rule.ruleType === "DECIMAL_SCALE") return { scale: rule.scale };
  return rule.value === undefined || rule.value === "" ? {} : { value: rule.value };
}

function fieldPayload(field: FieldDefinition, overrides: Partial<FieldDefinition> = {}): Partial<FieldDefinition> {
  return {
    groupId: field.groupId,
    fieldCode: field.fieldCode,
    columnName: field.columnName,
    label: field.label,
    dataType: field.dataType,
    componentType: field.componentType,
    description: field.description,
    placeholder: field.placeholder,
    required: field.required,
    uniqueValue: field.uniqueValue,
    businessKey: field.businessKey,
    indexed: field.indexed,
    searchable: field.searchable,
    sortable: field.sortable,
    exportable: field.exportable,
    maxLength: field.maxLength,
    precision: field.precision,
    scale: field.scale,
    defaultValue: field.defaultValue,
    displayOrder: field.displayOrder,
    gridSpan: field.gridSpan,
    readOnly: field.readOnly,
    hidden: field.hidden,
    helpText: field.helpText,
    lookupSourceId: field.lookupSourceId,
    lookup: field.lookup,
    validations: field.validations?.map(({ id: _id, ...rule }) => rule),
    ...overrides,
  };
}

function DesignerPreview({ templateCode, templateName, version, groups, fields }: {
  templateCode: string;
  templateName: string;
  version: number;
  groups: FieldGroup[];
  fields: FieldDefinition[];
}) {
  const { control } = useRuntimeForm();
  const metadata: FormMetadata = {
    templateCode,
    templateName,
    version,
    groups: groups.map((group) => ({ ...group, fields: fields.filter((field) => field.groupId === group.id) })),
  };
  return <div className="designer-runtime-preview"><DynamicFormRenderer metadata={metadata} control={control} disabled /></div>;
}

function MigrationPlanCard({ plan, loading, error }: { plan?: MigrationPlan; loading: boolean; error: unknown }) {
  if (loading) return <div className="migration-inline-state"><Spin size="small" /><span>Đang kiểm tra ảnh hưởng đến dữ liệu cũ...</span></div>;
  if (error) return <Alert type="error" showIcon message="Không thể kiểm tra dữ liệu cũ" description={getErrorMessage(error)} />;
  if (!plan) return null;
  const shownChanges = plan.changes.slice(0, 6);
  const severityColor = { SAFE: "green", WARNING: "gold", BLOCKING: "red" } as const;
  const changeLabel = { ADDED: "Thêm", MODIFIED: "Sửa", REMOVED: "Xóa" } as const;

  return (
    <div className="migration-details">
      <Alert
        type={plan.compatible ? "success" : "error"}
        showIcon
        message={plan.compatible ? "Có thể chuyển dữ liệu an toàn" : "Cần xử lý trước khi tạo bảng dữ liệu"}
        description={plan.compatible ? "Dữ liệu hiện có sẽ được giữ nguyên khi áp dụng cấu hình mới." : plan.blockers[0]}
      />
      <div className="migration-route">
        {plan.sourceVersion ? <><span>v{plan.sourceVersion}</span><ArrowRightOutlined /><span>v{plan.targetVersion}</span></> : <span>Khởi tạo v{plan.targetVersion}</span>}
        <small>{plan.sourceVersion ? `${plan.sourceRecordCount} hồ sơ sẽ được chuyển` : "Không có dữ liệu cũ"}</small>
      </div>
      <div className="migration-counts">
        <span><strong>{plan.addedFields}</strong><small>Thêm mới</small></span>
        <span><strong>{plan.modifiedFields}</strong><small>Thay đổi</small></span>
        <span><strong>{plan.removedFields}</strong><small>Xóa</small></span>
      </div>
      {shownChanges.length > 0 ? (
        <List
          className="migration-change-list"
          size="small"
          dataSource={shownChanges}
          renderItem={(change) => (
            <List.Item>
              <div><Tag color={severityColor[change.severity]}>{changeLabel[change.changeType]}</Tag><strong>{change.label}</strong></div>
              <small>{change.message}</small>
            </List.Item>
          )}
        />
      ) : <Alert type="success" showIcon message="Không có thay đổi cột cần chuyển" />}
      {plan.changes.length > shownChanges.length && <div className="migration-more">Và {plan.changes.length - shownChanges.length} thay đổi khác.</div>}
    </div>
  );
}

export function TemplateDesignerPage() {
  const { templateId = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const [groupForm] = Form.useForm<Partial<FieldGroup>>();
  const [fieldForm] = Form.useForm<FieldFormValue>();
  const [groupOpen, setGroupOpen] = useState(false);
  const [fieldOpen, setFieldOpen] = useState(false);
  const [fieldTab, setFieldTab] = useState("basic");
  const [editingGroup, setEditingGroup] = useState<FieldGroup | null>(null);
  const [editingField, setEditingField] = useState<FieldDefinition | null>(null);
  const [selectedGroupId, setSelectedGroupId] = useState<string>();
  const [ddlOpen, setDdlOpen] = useState(false);
  const [validation, setValidation] = useState<ValidationResult>();
  const [validationOpen, setValidationOpen] = useState(false);
  const [migrationOpen, setMigrationOpen] = useState(false);
  const [storageOpen, setStorageOpen] = useState(false);
  const [canvasMode, setCanvasMode] = useState<"DESIGN" | "PREVIEW">("DESIGN");
  const [draggedFieldId, setDraggedFieldId] = useState<string>();

  const templateQuery = useQuery({ queryKey: ["template", templateId], queryFn: () => templateApi.get(templateId), enabled: Boolean(templateId) });
  const versionsQuery = useQuery({ queryKey: ["versions", templateId], queryFn: () => templateApi.versions(templateId), enabled: Boolean(templateId) });
  const [selectedVersionId, setSelectedVersionId] = useState<string>();
  const versions = versionsQuery.data ?? [];
  const currentVersion = versions.find((item) => item.id === selectedVersionId)
    ?? versions[0]
    ?? versions.find((item) => item.id === templateQuery.data?.currentVersionId);
  const versionId = currentVersion?.id;

  const groupsQuery = useQuery({ queryKey: ["groups", versionId], queryFn: () => templateApi.groups(versionId!), enabled: Boolean(versionId) });
  const fieldsQuery = useQuery({ queryKey: ["fields", versionId], queryFn: () => templateApi.fields(versionId!), enabled: Boolean(versionId) });
  const lookupQuery = useQuery({ queryKey: ["lookup-sources"], queryFn: templateApi.lookupSources });
  const ddlQuery = useQuery({ queryKey: ["ddl", versionId], queryFn: () => templateApi.ddl(versionId!), enabled: ddlOpen && Boolean(versionId) });
  const migrationQuery = useQuery({
    queryKey: ["migration-plan", versionId],
    queryFn: () => templateApi.migrationPlan(versionId!),
    enabled: Boolean(versionId) && currentVersion?.status !== "PUBLISHED" && currentVersion?.status !== "ARCHIVED",
  });
  const storageQuery = useQuery({ queryKey: ["storage-check", versionId], queryFn: () => templateApi.storageCheck(versionId!), enabled: storageOpen && Boolean(versionId) });
  const formRulesQuery = useQuery({ queryKey: ["form-rules", versionId], queryFn: () => templateApi.formRules(versionId!), enabled: Boolean(versionId) });

  const groups = useMemo(() => [...(groupsQuery.data ?? [])].sort((a, b) => a.displayOrder - b.displayOrder), [groupsQuery.data]);
  const fields = useMemo(() => [...(fieldsQuery.data ?? [])].sort((a, b) => a.displayOrder - b.displayOrder), [fieldsQuery.data]);
  const isEditable = currentVersion?.status === "DRAFT" || currentVersion?.status === "VALIDATED";
  const editableVersion = versions.find((item) => item.status === "DRAFT" || item.status === "VALIDATED");

  const refreshDesigner = () => {
    void queryClient.invalidateQueries({ queryKey: ["groups", versionId] });
    void queryClient.invalidateQueries({ queryKey: ["fields", versionId] });
    void queryClient.invalidateQueries({ queryKey: ["versions", templateId] });
    void queryClient.invalidateQueries({ queryKey: ["migration-plan", versionId] });
  };

  const groupMutation = useMutation({
    mutationFn: (value: Partial<FieldGroup>) => {
      const payload = { ...value, code: value.code || toGroupCode(value.label ?? "Nhóm trường") };
      return editingGroup ? templateApi.updateGroup(editingGroup.id, payload) : templateApi.addGroup(versionId!, payload);
    },
    onSuccess: () => { refreshDesigner(); setGroupOpen(false); message.success("Đã lưu nhóm thông tin"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const fieldMutation = useMutation({
    mutationFn: (value: FieldFormValue) => {
      const payload: Partial<FieldDefinition> = {
        ...value,
        fieldCode: value.fieldCode || toFieldCode(value.label ?? "Trường dữ liệu"),
        componentType: value.componentType ?? componentByType[value.dataType ?? "STRING"],
        validations: value.validations?.filter((rule) => rule.ruleType).map((rule, index) => ({
          ruleType: rule.ruleType,
          ruleConfig: ruleConfig(rule),
          errorMessage: rule.errorMessage,
          displayOrder: index,
        })),
        lookup: value.dataType === "LIST" ? { lookupCode: value.lookupCode, autocomplete: value.autocomplete, selectionMode: "SINGLE", allowClear: true } : undefined,
      };
      if (value.businessKey) {
        payload.required = true;
        payload.uniqueValue = true;
        payload.indexed = true;
        payload.searchable = true;
      }
      delete (payload as Record<string, unknown>).lookupCode;
      delete (payload as Record<string, unknown>).autocomplete;
      return editingField ? templateApi.updateField(editingField.id, payload) : templateApi.addField(versionId!, payload);
    },
    onSuccess: () => { refreshDesigner(); setFieldOpen(false); message.success("Đã lưu trường dữ liệu"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const removeGroup = useMutation({ mutationFn: templateApi.removeGroup, onSuccess: refreshDesigner, onError: (error) => message.error(getErrorMessage(error)) });
  const removeField = useMutation({ mutationFn: templateApi.removeField, onSuccess: refreshDesigner, onError: (error) => message.error(getErrorMessage(error)) });
  const duplicateFieldMutation = useMutation({
    mutationFn: (field: FieldDefinition) => {
      const suffix = Date.now().toString().slice(-5);
      return templateApi.addField(versionId!, fieldPayload(field, {
        id: undefined,
        fieldCode: `${field.fieldCode}Copy${suffix}`.slice(0, 100),
        columnName: undefined,
        label: `${field.label} - bản sao`,
        displayOrder: fields.filter((item) => item.groupId === field.groupId).length + 1,
        uniqueValue: false,
        businessKey: false,
      }));
    },
    onSuccess: () => { refreshDesigner(); message.success("Đã nhân bản trường dữ liệu"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const duplicateGroupMutation = useMutation({
    mutationFn: async (group: FieldGroup) => {
      const suffix = Date.now().toString().slice(-4);
      const copiedGroup = await templateApi.addGroup(versionId!, {
        code: `${group.code}_COPY_${suffix}`.slice(0, 100),
        label: `${group.label} - bản sao`,
        description: group.description,
        displayOrder: groups.length + 1,
        columnCount: group.columnCount ?? 3,
        collapsible: group.collapsible ?? true,
        defaultCollapsed: false,
        repeatable: false,
      });
      const sourceFields = fields.filter((field) => field.groupId === group.id);
      await Promise.all(sourceFields.map((field, index) => templateApi.addField(versionId!, fieldPayload(field, {
        id: undefined,
        groupId: copiedGroup.id,
        fieldCode: `${field.fieldCode}Copy${suffix}`.slice(0, 100),
        columnName: undefined,
        displayOrder: index + 1,
        uniqueValue: false,
        businessKey: false,
      }))));
      return copiedGroup;
    },
    onSuccess: () => { refreshDesigner(); message.success("Đã nhân bản nhóm và các trường"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const reorderFieldsMutation = useMutation({
    mutationFn: async ({ source, target }: { source: FieldDefinition; target: FieldDefinition }) => {
      await Promise.all([
        templateApi.updateField(source.id, fieldPayload(source, { displayOrder: target.displayOrder })),
        templateApi.updateField(target.id, fieldPayload(target, { displayOrder: source.displayOrder })),
      ]);
    },
    onSuccess: refreshDesigner,
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const validateMutation = useMutation({
    mutationFn: () => templateApi.validate(versionId!),
    onSuccess: (result) => { setValidation(result); setValidationOpen(true); refreshDesigner(); result.valid ? message.success("Cấu hình hợp lệ") : message.warning("Cấu hình còn lỗi cần xử lý"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const revalidate = async () => {
    const result = await templateApi.validate(versionId!);
    setValidation(result);
    refreshDesigner();
    return result;
  };
  const quickFixMutation = useMutation({
    mutationFn: async ({ issue, field }: { issue: { code: string }; field: FieldDefinition }) => {
      const overrides: Partial<FieldDefinition> = {};
      if (issue.code === "SYSTEM_COLUMN") {
        const base = toColumnName(field.fieldCode);
        let candidate = base;
        let suffix = 2;
        while (fields.some((item) => item.id !== field.id && item.columnName?.toLowerCase() === candidate.toLowerCase())) {
          candidate = `${base.slice(0, 59)}_${suffix++}`;
        }
        overrides.columnName = candidate;
      } else if (issue.code === "MISSING_MAX_LENGTH") {
        overrides.maxLength = field.dataType === "PHONE" ? 20 : 255;
      } else if (issue.code === "INVALID_DECIMAL") {
        overrides.precision = 18;
        overrides.scale = 2;
      } else if (issue.code === "UNUSED_LOOKUP") {
        overrides.lookupSourceId = undefined;
        overrides.lookup = undefined;
      } else if (issue.code === "SEARCH_WITHOUT_INDEX") {
        overrides.indexed = true;
      } else if (issue.code === "INCOMPATIBLE_COMPONENT") {
        overrides.componentType = componentByType[field.dataType];
      } else if (issue.code === "BUSINESS_KEY_INCONSISTENT") {
        overrides.businessKey = true;
        overrides.required = true;
        overrides.uniqueValue = true;
        overrides.indexed = true;
        overrides.searchable = true;
      }
      await templateApi.updateField(field.id, fieldPayload(field, overrides));
      return revalidate();
    },
    onSuccess: () => message.success("Đã sửa và kiểm tra lại cấu hình"),
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const selectBusinessKeyMutation = useMutation({
    mutationFn: async (field: FieldDefinition) => {
      await templateApi.updateField(field.id, fieldPayload(field, {
        businessKey: true,
        required: true,
        uniqueValue: true,
        indexed: true,
        searchable: true,
      }));
      return revalidate();
    },
    onSuccess: () => message.success("Đã chọn mã định danh chính và kiểm tra lại"),
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const generateMutation = useMutation({
    mutationFn: () => templateApi.generate(versionId!),
    onSuccess: () => {
      refreshDesigner();
      const copied = migrationQuery.data?.sourceRecordCount ?? 0;
      message.success(copied > 0 ? `Đã tạo bảng và chuyển ${copied} hồ sơ cũ` : "Đã tạo bảng lưu trữ dữ liệu");
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const publishMutation = useMutation({
    mutationFn: () => templateApi.publish(versionId!),
    onSuccess: () => {
      refreshDesigner();
      void queryClient.invalidateQueries({ queryKey: ["template", templateId] });
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
      message.success("Biểu mẫu đã được đưa vào sử dụng");
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const createVersionMutation = useMutation({
    mutationFn: () => templateApi.createVersion(templateId),
    onSuccess: (version) => { void queryClient.invalidateQueries({ queryKey: ["versions", templateId] }); setSelectedVersionId(version.id); message.success(`Đã tạo bản chỉnh sửa v${version.versionNo}`); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const addOwnerRuleMutation = useMutation({
    mutationFn: () => templateApi.addFormRule(versionId!, {
      ruleType: "AT_LEAST_ONE_FILLED",
      fieldCodes: fields.filter((field) => /owner[12]Name/i.test(field.fieldCode)).map((field) => field.fieldCode),
      errorMessage: "Cần nhập thông tin ít nhất một chủ sử dụng đất (vợ hoặc chồng).",
      displayOrder: 0,
    }),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["form-rules", versionId] }); void queryClient.invalidateQueries({ queryKey: ["migration-plan", versionId] }); message.success("Đã thêm điều kiện chủ sử dụng đất"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const cancelGeneratedMutation = useMutation({
    mutationFn: () => templateApi.cancelGenerated(versionId!),
    onSuccess: () => { refreshDesigner(); message.success("Đã hủy bảng chưa đưa vào sử dụng; bạn có thể sửa lại cấu hình."); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const purgeArchivedMutation = useMutation({
    mutationFn: () => templateApi.purgeArchived(versionId!, "Đã kiểm tra dữ liệu chuyển sang phiên bản đang sử dụng."),
    onSuccess: () => { refreshDesigner(); setStorageOpen(false); message.success("Đã giải phóng bảng phiên bản cũ; lịch sử cấu hình vẫn được giữ."); },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const openRevision = () => {
    if (editableVersion) {
      setSelectedVersionId(editableVersion.id);
      message.info(`Đã mở bản chỉnh sửa v${editableVersion.versionNo}`);
      return;
    }
    createVersionMutation.mutate();
  };

  const openGroup = (group?: FieldGroup) => {
    setEditingGroup(group ?? null);
    groupForm.resetFields();
    groupForm.setFieldsValue(group ? {
      id: group.id,
      code: group.code,
      label: group.label,
      description: group.description,
      displayOrder: group.displayOrder,
      columnCount: group.columnCount,
      collapsible: group.collapsible,
    } : { displayOrder: groups.length + 1, columnCount: 3, collapsible: true, defaultCollapsed: false, repeatable: false });
    setGroupOpen(true);
  };
  const openField = (groupId: string, field?: FieldDefinition, tab = "basic") => {
    setSelectedGroupId(groupId);
    setEditingField(field ?? null);
    fieldForm.resetFields();
    setFieldTab(tab);
    if (field) {
      const { lookup: _lookup, validations: _validations, ...editableField } = field;
      fieldForm.setFieldsValue({
        ...editableField,
        businessKey: Boolean(field.businessKey),
        lookupCode: field.lookup?.lookupCode,
        autocomplete: field.lookup?.autocomplete,
        validations: field.validations?.map((rule) => ({
          ruleType: rule.ruleType,
          value: String(rule.ruleConfig?.value ?? rule.ruleConfig?.pattern ?? ""),
          min: rule.ruleConfig?.min ? String(rule.ruleConfig.min) : undefined,
          max: rule.ruleConfig?.max ? String(rule.ruleConfig.max) : undefined,
          scale: rule.ruleConfig?.scale === undefined ? undefined : Number(rule.ruleConfig.scale),
          errorMessage: rule.errorMessage,
        })),
      });
    } else {
      const columnCount = groups.find((group) => group.id === groupId)?.columnCount ?? 3;
      const defaultSpan = Math.max(1, Math.floor(12 / columnCount));
      fieldForm.setFieldsValue({ groupId, dataType: "STRING", componentType: "TEXT", maxLength: 255, displayOrder: fields.filter((item) => item.groupId === groupId).length + 1, gridSpan: defaultSpan, searchable: false, sortable: false, exportable: true });
    }
    setFieldOpen(true);
  };

  const applyDataTypeDefaults = (type: FieldDataType) => {
    fieldForm.setFieldsValue({ maxLength: undefined, precision: undefined, scale: undefined });
    const defaults: {
      componentType: ComponentType;
      validations: ValidationFormValue[];
      maxLength?: number;
      precision?: number;
      scale?: number;
    } = {
      componentType: componentByType[type],
      validations: suggestedValidations(type),
    };
    if (type === "STRING" || type === "EMAIL") defaults.maxLength = 255;
    if (type === "PHONE") defaults.maxLength = 20;
    if (type === "TEXTAREA") defaults.maxLength = 4000;
    if (type === "DECIMAL") {
      defaults.precision = 18;
      defaults.scale = 2;
    }
    fieldForm.setFieldsValue({ ...defaults, businessKey: businessKeyTypes.includes(type) ? fieldForm.getFieldValue("businessKey") : false });
  };

  const applyBusinessKey = (checked: boolean) => {
    fieldForm.setFieldValue("businessKey", checked);
    fieldForm.setFieldsValue({
      required: checked,
      uniqueValue: checked,
      indexed: checked,
      searchable: checked,
    });
  };

  if (templateQuery.isLoading || versionsQuery.isLoading) return <PageLoading />;
  if (templateQuery.isError) return <PageError message={getErrorMessage(templateQuery.error)} onRetry={() => void templateQuery.refetch()} />;
  const template = templateQuery.data;
  const hasPublishedVersion = versions.some((version) => version.status === "PUBLISHED");
  const eligibleBusinessKeyFields = fields.filter((field) => businessKeyTypes.includes(field.dataType));
  const businessKeyMenu = {
    items: eligibleBusinessKeyFields.map((field) => ({ key: field.id, label: field.label })),
    onClick: ({ key }: { key: string }) => {
      const field = fields.find((item) => item.id === key);
      if (field) selectBusinessKeyMutation.mutate(field);
    },
  };
  const renderValidationIssue = (issue: ValidationIssue, type: "error" | "warning") => {
    const field = fields.find((item) => item.fieldCode === issue.fieldCode);
    let detail: string | undefined;
    if (field && issue.code === "SYSTEM_COLUMN") detail = `Đang dùng: ${field.columnName} · Đề xuất: ${toColumnName(field.fieldCode)}`;
    if (field && issue.code === "MISSING_MAX_LENGTH") detail = `Đang để trống · Đề xuất: ${field.dataType === "PHONE" ? 20 : 255} ký tự`;
    if (field && issue.code === "INVALID_DECIMAL") detail = `Đề xuất: tổng 18 chữ số, 2 chữ số sau dấu phẩy`;
    const action = (() => {
      if (["BUSINESS_KEY_MISSING", "BUSINESS_KEY_MULTIPLE"].includes(issue.code) && isEditable) {
        return <Dropdown menu={businessKeyMenu}><Button size="small" type="primary">Chọn mã định danh</Button></Dropdown>;
      }
      if (issue.code === "NO_FIELDS" && isEditable) {
        return <Button size="small" type="primary" onClick={() => groups[0] ? openField(groups[0].id) : openGroup()}>{groups[0] ? "Thêm trường" : "Tạo nhóm trường"}</Button>;
      }
      if (!field || !isEditable) return undefined;
      return <Space size={6}>
        {automaticFixCodes.has(issue.code) && <Button size="small" type="primary" loading={quickFixMutation.isPending} onClick={() => quickFixMutation.mutate({ issue, field })}>Sửa tự động</Button>}
        <Button size="small" onClick={() => { setValidationOpen(false); openField(field.groupId, field, issueTab(issue.code)); }}>Mở chi tiết</Button>
      </Space>;
    })();
    return (
      <Alert
        key={`${type}-${issue.fieldCode ?? "table"}-${issue.code}`}
        type={type}
        showIcon
        message={field?.label ?? (issue.code.startsWith("BUSINESS_KEY") ? "Mã định danh chính" : "Cấu hình bảng")}
        description={<div className="validation-issue-description">
          <span>{validationIssueMessages[issue.code] ?? issue.message}</span>
          {detail && <small>{detail}</small>}
          {action && <div className="validation-issue-actions">{action}</div>}
        </div>}
      />
    );
  };

  return (
    <div className="page-stack designer-page">
      <Card className="designer-command-card">
        <div className="designer-command-row">
          <Tooltip title="Về danh sách biểu mẫu">
            <Button
              aria-label="Về danh sách biểu mẫu"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate("/templates")}
            />
          </Tooltip>
          <div className="designer-command-title">
            <strong>{template?.name}</strong>
            <small>Cấu hình các nhóm và trường dữ liệu của biểu mẫu.</small>
          </div>
          <Space wrap className="designer-context-actions">
            <Select
              value={versionId}
              placeholder="Chọn phiên bản"
              style={{ minWidth: 190 }}
              options={versions.map((item) => ({ value: item.id, label: `v${item.versionNo} · ${lifecycleLabels[item.status]}` }))}
              onChange={setSelectedVersionId}
            />
            <Tooltip title={hasPublishedVersion ? "Mở danh sách hồ sơ" : "Cần tạo bảng và đưa biểu mẫu vào sử dụng trước."}>
              <span><Button icon={<EyeOutlined />} disabled={!template?.code || !hasPublishedVersion} onClick={() => navigate(`/workspaces/${template?.code}`)}>Xem hồ sơ</Button></span>
            </Tooltip>
            {currentVersion && !isEditable && currentVersion.status !== "GENERATED" && (
              <Button type="primary" onClick={openRevision} loading={createVersionMutation.isPending}>
                {editableVersion ? `Mở bản chỉnh sửa v${editableVersion.versionNo}` : "Tạo bản chỉnh sửa"}
              </Button>
            )}
          </Space>
        </div>
        {currentVersion?.status === "PUBLISHED" && (
          <Alert
            className="version-lock-alert"
            type="info"
            showIcon
            message="Cấu hình này đang được sử dụng để nhập liệu nên chỉ có thể xem."
            description="Muốn thay đổi trường dữ liệu, hãy tạo bản chỉnh sửa mới. Dữ liệu hiện tại vẫn được giữ nguyên."
          />
        )}
        {currentVersion?.status === "ARCHIVED" && <Alert className="version-lock-alert" type="warning" showIcon message="Đây là phiên bản cũ và chỉ được phép xem." />}
      </Card>

      {!versionId ? (
        <Card><Empty description="Biểu mẫu chưa có phiên bản"><Button type="primary" onClick={() => createVersionMutation.mutate()}>Tạo phiên bản đầu tiên</Button></Empty></Card>
      ) : (
        <>
          <Card
            className="designer-canvas"
            title={<div><div className="section-title">Các trường của biểu mẫu</div><small className="section-description">{groups.length} nhóm · {fields.length} trường dữ liệu</small></div>}
            extra={<Space wrap>
              <Segmented
                value={canvasMode}
                onChange={(value) => setCanvasMode(value as "DESIGN" | "PREVIEW")}
                options={[{ value: "DESIGN", label: "Thiết kế" }, { value: "PREVIEW", label: "Xem trước" }]}
              />
              <Button type="primary" icon={<PlusOutlined />} disabled={!isEditable} onClick={() => openGroup()}>Thêm nhóm trường</Button>
            </Space>}
          >
              {(groupsQuery.isLoading || fieldsQuery.isLoading) ? <Spin /> : groups.length === 0 ? (
                <Empty description="Bảng chưa có nhóm trường"><Button disabled={!isEditable} onClick={() => openGroup()}>Tạo nhóm trường đầu tiên</Button></Empty>
              ) : canvasMode === "PREVIEW" ? (
                <DesignerPreview
                  templateCode={template?.code ?? "PREVIEW"}
                  templateName={template?.name ?? "Xem trước biểu mẫu"}
                  version={currentVersion?.versionNo ?? 1}
                  groups={groups}
                  fields={fields}
                />
              ) : (
                <Collapse
                  defaultActiveKey={groups.map((group) => group.id)}
                  items={groups.map((group) => {
                    const groupFields = fields.filter((field) => field.groupId === group.id);
                    return {
                      key: group.id,
                      label: <div className="group-heading"><span><strong>{group.label}</strong><small>{groupFields.length} trường dữ liệu</small></span></div>,
                      extra: <Space onClick={(event) => event.stopPropagation()}>
                        <Tooltip title="Nhân bản nhóm"><Button type="text" icon={<CopyOutlined />} disabled={!isEditable} loading={duplicateGroupMutation.isPending} onClick={() => duplicateGroupMutation.mutate(group)} /></Tooltip>
                        <Tooltip title="Sửa nhóm"><Button type="text" icon={<EditOutlined />} disabled={!isEditable} onClick={() => openGroup(group)} /></Tooltip>
                        <Popconfirm title="Xóa nhóm và các trường bên trong?" onConfirm={() => removeGroup.mutate(group.id)}><Button danger type="text" icon={<DeleteOutlined />} disabled={!isEditable} /></Popconfirm>
                      </Space>,
                      children: <div>
                        {groupFields.length === 0 ? <div className="inline-empty">Nhóm chưa có trường dữ liệu.</div> : (
                          <div className="field-grid designer-field-grid">
                            {groupFields.map((field) => (
                              <div
                                className="field-tile"
                                key={field.id}
                                draggable={isEditable}
                                style={{ gridColumn: `span ${field.gridSpan ?? 4}` }}
                                onDragStart={() => setDraggedFieldId(field.id)}
                                onDragOver={(event) => event.preventDefault()}
                                onDrop={() => {
                                  const source = fields.find((item) => item.id === draggedFieldId);
                                  if (source && source.groupId === field.groupId && source.id !== field.id) reorderFieldsMutation.mutate({ source, target: field });
                                  setDraggedFieldId(undefined);
                                }}
                              >
                                <div className="field-tile-top"><Tag>{dataTypeLabels[field.dataType]}</Tag><Space size={2}><Button type="text" size="small" icon={<CopyOutlined />} disabled={!isEditable} onClick={() => duplicateFieldMutation.mutate(field)} /><Button type="text" size="small" icon={<EditOutlined />} disabled={!isEditable} onClick={() => openField(group.id, field)} /><Popconfirm title="Xóa trường này?" onConfirm={() => removeField.mutate(field.id)}><Button danger type="text" size="small" icon={<DeleteOutlined />} disabled={!isEditable} /></Popconfirm></Space></div>
                                <strong>{field.label}{field.required && <span className="required-mark"> *</span>}</strong>
                              </div>
                            ))}
                          </div>
                        )}
                        <Button className="add-field-button" type="dashed" icon={<PlusOutlined />} disabled={!isEditable} onClick={() => openField(group.id)}>Thêm trường vào nhóm</Button>
                      </div>,
                    };
                  })}
                />
              )}
          </Card>

          <Card className="designer-form-rules" title={<div><div className="section-title">Điều kiện hoàn tất hồ sơ</div><small className="section-description">Kiểm tra các trường liên quan khi người dùng bấm Hoàn tất nhập liệu.</small></div>}>
            {(formRulesQuery.data ?? []).map((rule) => <div className="form-rule-row" key={rule.id}><strong>{rule.ruleType === "AT_LEAST_ONE_FILLED" ? "Ít nhất một trường có dữ liệu" : rule.ruleType}</strong><span>{rule.fieldCodes.map((code) => fields.find((field) => field.fieldCode === code)?.label ?? code).join(" · ")}</span></div>)}
            {!formRulesQuery.data?.length && <div className="inline-empty">Chưa có điều kiện liên trường.</div>}
            <Button type="dashed" icon={<PlusOutlined />} disabled={!isEditable || fields.filter((field) => /owner[12]Name/i.test(field.fieldCode)).length < 2 || Boolean((formRulesQuery.data ?? []).some((rule) => rule.ruleType === "AT_LEAST_ONE_FILLED"))} loading={addOwnerRuleMutation.isPending} onClick={() => addOwnerRuleMutation.mutate()}>Thêm điều kiện vợ hoặc chồng</Button>
          </Card>

          {(isEditable || currentVersion?.status === "GENERATED") && (
            <Card className="designer-action-bar">
              <div className="designer-action-layout">
                <div className="designer-action-copy">
                  <strong>{currentVersion?.status === "GENERATED" ? "Bảng dữ liệu đã sẵn sàng" : "Hoàn tất cấu hình"}</strong>
                  <small>{currentVersion?.status === "GENERATED" ? "Đưa biểu mẫu vào sử dụng khi đã kiểm tra xong." : "Kiểm tra trước khi tạo bảng dữ liệu."}</small>
                </div>
                <Space wrap className="designer-action-buttons">
                  <Button icon={<CheckCircleOutlined />} loading={validateMutation.isPending} onClick={() => validateMutation.mutate()}>Kiểm tra</Button>
                  {validation && (
                    <Button danger={!validation.valid} onClick={() => setValidationOpen(true)}>
                      {validation.valid ? "Kết quả kiểm tra" : `${validation.errors.length + validation.warnings.length} mục cần xem`}
                    </Button>
                  )}
                  <Button
                    type={currentVersion?.status === "GENERATED" ? "default" : "primary"}
                    icon={<DatabaseOutlined />}
                    disabled={!isEditable || migrationQuery.isLoading || migrationQuery.data?.compatible === false}
                    loading={generateMutation.isPending}
                    onClick={() => modal.confirm({
                      title: "Tạo bảng và chuyển dữ liệu?",
                      content: migrationQuery.data?.sourceVersion
                        ? `Tạo bảng cho phiên bản ${migrationQuery.data.targetVersion} và chuyển ${migrationQuery.data.sourceRecordCount} hồ sơ từ phiên bản ${migrationQuery.data.sourceVersion}.`
                        : "Tạo bảng PostgreSQL đầu tiên từ cấu hình trường hiện tại.",
                      okText: "Tạo bảng dữ liệu",
                      onOk: () => generateMutation.mutateAsync(),
                    })}
                  >Tạo bảng dữ liệu</Button>
                  <Button
                    type={currentVersion?.status === "GENERATED" ? "primary" : "default"}
                    className="publish-button"
                    icon={<RocketOutlined />}
                    disabled={currentVersion?.status !== "GENERATED"}
                    loading={publishMutation.isPending}
                    onClick={() => modal.confirm({
                      title: `Đưa phiên bản ${currentVersion?.versionNo} vào sử dụng?`,
                      content: "Phiên bản này sẽ trở thành biểu mẫu nhập liệu chính. Phiên bản đang dùng hiện tại vẫn được lưu lại để tra cứu.",
                      okText: "Đưa vào sử dụng",
                      onOk: () => publishMutation.mutateAsync(),
                    })}
                  >Đưa vào sử dụng</Button>
                  <Dropdown
                    trigger={["click"]}
                    menu={{ items: [
                      { key: "migration", icon: <DatabaseOutlined />, label: "Ảnh hưởng dữ liệu", onClick: () => setMigrationOpen(true) },
                      { key: "technical", icon: <CodeOutlined />, label: "Thông tin kỹ thuật", onClick: () => setDdlOpen(true) },
                       ...(currentVersion?.status === "GENERATED" ? [{ key: "cancel-generated", icon: <DeleteOutlined />, label: "Hủy bảng chưa sử dụng", onClick: () => modal.confirm({ title: "Hủy bảng phiên bản này?", content: "Bảng chưa đưa vào sử dụng sẽ bị xóa; cấu hình vẫn được giữ để bạn tiếp tục sửa.", okText: "Hủy bảng", okButtonProps: { danger: true }, onOk: () => cancelGeneratedMutation.mutateAsync() }) }] : []),
                       ...(currentVersion?.status === "ARCHIVED" ? [{ key: "storage", icon: <DatabaseOutlined />, label: "Kiểm tra giải phóng bảng cũ", onClick: () => setStorageOpen(true) }] : []),
                    ] }}
                  >
                    <Button icon={<MoreOutlined />}>Xem thêm</Button>
                  </Dropdown>
                </Space>
              </div>
              {migrationQuery.data?.compatible === false && (
                <Alert
                  type="error"
                  showIcon
                  message="Chưa thể tạo bảng vì cấu hình ảnh hưởng đến dữ liệu cũ."
                  action={<Button size="small" onClick={() => setMigrationOpen(true)}>Xem cách xử lý</Button>}
                />
              )}
            </Card>
          )}
        </>
      )}

      <Drawer
        title={validation?.valid ? "Kết quả kiểm tra: Cấu hình hợp lệ" : "Kết quả kiểm tra cấu hình"}
        width="min(760px, 100vw)"
        open={validationOpen}
        onClose={() => setValidationOpen(false)}
      >
        {validation && (
          <div className="validation-result-stack">
            {(validation.errors.length > 0 || validation.warnings.length > 0) && (
              <Alert
                type={validation.errors.length ? "error" : "warning"}
                showIcon
                message={`${validation.errors.length} lỗi phải sửa · ${validation.warnings.length} khuyến nghị`}
                description={validation.errors.length ? "Cần xử lý hết lỗi màu đỏ trước khi tạo bảng. Khuyến nghị màu vàng không chặn thao tác." : "Cấu hình có thể tạo bảng; các mục màu vàng chỉ nhằm tối ưu sử dụng."}
              />
            )}
            <div className="designer-validation-grid">
              {validation.errors.map((issue) => renderValidationIssue(issue, "error"))}
              {validation.warnings.map((issue) => renderValidationIssue(issue, "warning"))}
              {!validation.errors.length && !validation.warnings.length && <Alert type="success" showIcon message="Sẵn sàng tạo bảng dữ liệu" description="Không phát hiện lỗi kiểu dữ liệu, điều kiện nhập, mã định danh hoặc chỉ mục." />}
            </div>
          </div>
        )}
      </Drawer>

      <Drawer title="Ảnh hưởng đến dữ liệu hiện có" width={640} open={migrationOpen} onClose={() => setMigrationOpen(false)}>
        <MigrationPlanCard plan={migrationQuery.data} loading={migrationQuery.isLoading} error={migrationQuery.error} />
      </Drawer>

      <Modal title="Kiểm tra giải phóng bảng phiên bản cũ" open={storageOpen} onCancel={() => setStorageOpen(false)} footer={<Space><Button onClick={() => setStorageOpen(false)}>Đóng</Button><Button danger type="primary" disabled={!storageQuery.data?.eligible} loading={purgeArchivedMutation.isPending} onClick={() => purgeArchivedMutation.mutate()}>Giải phóng bảng</Button></Space>}>
        {storageQuery.isLoading ? <Spin /> : storageQuery.isError ? <Alert type="error" message={getErrorMessage(storageQuery.error)} /> : storageQuery.data && <div className="storage-check-panel"><Alert type={storageQuery.data.eligible ? "success" : "warning"} showIcon message={storageQuery.data.eligible ? "Có thể giải phóng an toàn" : "Chưa đủ điều kiện giải phóng"} description={storageQuery.data.blockers.length ? storageQuery.data.blockers.join(" ") : "Mọi hồ sơ từ phiên bản cũ đều đã tồn tại ở phiên bản đang sử dụng."} /><div className="migration-counts"><span><strong>{storageQuery.data.sourceRecordCount}</strong><small>Phiên bản cũ</small></span><span><strong>{storageQuery.data.currentRecordCount}</strong><small>Đang sử dụng</small></span><span><strong>{storageQuery.data.missingRecordCount}</strong><small>Chưa chuyển</small></span></div></div>}
      </Modal>

      <Modal title={editingGroup ? "Sửa nhóm thông tin" : "Thêm nhóm thông tin"} open={groupOpen} onCancel={() => setGroupOpen(false)} onOk={() => groupForm.submit()} confirmLoading={groupMutation.isPending} okText="Lưu">
        <Form form={groupForm} layout="vertical" onFinish={(value) => groupMutation.mutate(value)}>
          <Form.Item name="code" hidden><Input /></Form.Item>
          <Form.Item name="label" label="Tên nhóm thông tin" rules={[{ required: true, message: "Nhập tên nhóm thông tin" }]}>
            <Input placeholder="Ví dụ: Thông tin phát hành" onBlur={(event) => { if (!groupForm.getFieldValue("code")) groupForm.setFieldValue("code", toGroupCode(event.target.value)); }} />
          </Form.Item>
          <Form.Item name="description" label="Mô tả ngắn"><Input.TextArea rows={2} placeholder="Có thể bỏ trống" /></Form.Item>
          <Form.Item name="columnCount" label="Số trường trên một hàng"><Select options={[1, 2, 3, 4].map((value) => ({ value, label: `${value} trường` }))} /></Form.Item>
          <Collapse ghost size="small" items={[{ key: "display-options", label: "Tùy chọn hiển thị", children: <Row gutter={12}><Col span={12}><Form.Item name="displayOrder" label="Thứ tự nhóm"><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col><Col span={12}><Form.Item name="collapsible" valuePropName="checked"><Checkbox>Cho phép thu gọn</Checkbox></Form.Item></Col></Row> }]} />
        </Form>
      </Modal>

      <Drawer title={editingField ? "Sửa trường dữ liệu" : "Thêm trường dữ liệu"} width={620} open={fieldOpen} onClose={() => setFieldOpen(false)} extra={<Button type="primary" loading={fieldMutation.isPending} onClick={() => fieldForm.submit()}>Lưu</Button>}>
        <Form form={fieldForm} layout="vertical" onFinish={(value) => fieldMutation.mutate({ ...value, groupId: value.groupId ?? selectedGroupId })} requiredMark="optional">
          <Tabs activeKey={fieldTab} onChange={setFieldTab} items={[
            { key: "basic", label: "Thông tin", children: <>
              <Form.Item name="groupId" hidden><Input /></Form.Item>
              <Form.Item name="label" label="Tên hiển thị" rules={[{ required: true, message: "Nhập tên hiển thị của trường" }]}>
                <Input
                  placeholder="Ví dụ: Họ tên chủ sử dụng đất"
                  onBlur={(event) => {
                    if (!fieldForm.getFieldValue("fieldCode")) fieldForm.setFieldValue("fieldCode", toFieldCode(event.target.value));
                  }}
                />
              </Form.Item>
              <Form.Item name="dataType" label="Loại dữ liệu" rules={[{ required: true, message: "Chọn loại dữ liệu cho trường" }]} extra="Hệ thống tự chọn ô nhập và điều kiện kiểm tra phù hợp."><Select options={dataTypes} onChange={applyDataTypeDefaults} /></Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, next) => prev.dataType !== next.dataType}>{({ getFieldValue }) => {
                const type = getFieldValue("dataType") as FieldDataType;
                if (["STRING", "TEXTAREA", "EMAIL", "PHONE"].includes(type)) return <Form.Item name="maxLength" label="Số ký tự tối đa" extra="Hệ thống kiểm tra giới hạn này ở cả giao diện và backend."><InputNumber min={1} max={10000} style={{ width: "100%" }} /></Form.Item>;
                if (type === "DECIMAL") return <Row gutter={12}><Col span={12}><Form.Item name="precision" label="Tổng số chữ số"><InputNumber min={1} max={38} style={{ width: "100%" }} /></Form.Item></Col><Col span={12}><Form.Item name="scale" label="Số chữ số sau dấu phẩy"><InputNumber min={0} max={20} style={{ width: "100%" }} /></Form.Item></Col></Row>;
                if (type === "LIST") return <Card size="small" title="Danh mục lựa chọn"><Form.Item name="lookupCode" label="Chọn danh mục" rules={[{ required: true, message: "Chọn danh mục dữ liệu" }]}><Select showSearch optionFilterProp="label" placeholder="Chọn danh mục dùng chung" options={(lookupQuery.data ?? []).map((item) => ({ value: item.code, label: item.name }))} /></Form.Item><Form.Item name="autocomplete" valuePropName="checked"><Switch /> <span className="switch-label">Cho phép tìm trong danh sách</span></Form.Item></Card>;
                return null;
              }}</Form.Item>
              <Form.Item name="placeholder" label="Nội dung gợi ý"><Input placeholder="Nội dung hiển thị khi chưa nhập dữ liệu" /></Form.Item>
              <Form.Item name="defaultValue" label="Giá trị điền sẵn" extra="Dùng khi muốn hệ thống tự điền một giá trị ban đầu cho trường mới."><Input /></Form.Item>
              <Form.Item name="helpText" label="Hướng dẫn cho người nhập"><Input.TextArea rows={2} /></Form.Item>
              <Row gutter={12}>
                <Col span={8}><Form.Item name="displayOrder" label="Thứ tự hiển thị"><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
                <Col span={16}>
                  <Form.Item name="gridSpan" label="Độ rộng trên một hàng">
                    <Segmented block options={[{ value: 3, label: "1/4" }, { value: 4, label: "1/3" }, { value: 6, label: "1/2" }, { value: 8, label: "2/3" }, { value: 12, label: "Toàn hàng" }]} />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item noStyle shouldUpdate={(prev, next) => prev.businessKey !== next.businessKey}>{({ getFieldValue }) => (
                <div className="checkbox-grid">{[
                  ["required", "Bắt buộc nhập"], ["uniqueValue", "Không được trùng"], ["searchable", "Cho phép tìm kiếm"],
                ].map(([name, label]) => <Form.Item key={name} name={name} valuePropName="checked"><Checkbox disabled={Boolean(getFieldValue("businessKey"))}>{label}</Checkbox></Form.Item>)}</div>
              )}</Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, next) => prev.dataType !== next.dataType || prev.businessKey !== next.businessKey}>{({ getFieldValue }) => {
                const canBeBusinessKey = businessKeyTypes.includes(getFieldValue("dataType") as FieldDataType);
                return <div className="business-key-option">
                  <Form.Item name="businessKey" valuePropName="checked">
                    <Checkbox disabled={!canBeBusinessKey} onChange={(event) => applyBusinessKey(event.target.checked)}>Dùng làm mã định danh chính</Checkbox>
                  </Form.Item>
                  <small>{canBeBusinessKey
                    ? "Mỗi biểu mẫu chỉ có một mã định danh chính, ví dụ mã hồ sơ hoặc số seri. Hệ thống tự bật bắt buộc, không trùng, tìm kiếm và chỉ mục."
                    : "Loại dữ liệu này không phù hợp làm mã hồ sơ. Hãy dùng chuỗi ngắn, số nguyên, email hoặc số điện thoại."}</small>
                </div>;
              }}</Form.Item>
            </> },
            { key: "rules", label: "Điều kiện nhập", children: <>
              <Form.Item noStyle shouldUpdate>{({ getFieldsValue }) => {
                const rules = automaticRuleDescriptions(getFieldsValue() as FieldFormValue);
                return <div className="automatic-rule-panel"><strong>Điều kiện kiểm tra được áp dụng tự động</strong><small>Hệ thống tự sinh các điều kiện này từ kiểu dữ liệu và lựa chọn khóa định danh.</small><ul>{rules.map((rule) => <li key={rule}>{rule}</li>)}</ul></div>;
              }}</Form.Item>
              <Divider orientation="left">Điều kiện kiểm tra bổ sung</Divider>
              <Form.List name="validations">{(ruleFields, { add, remove }) => <Space direction="vertical" style={{ width: "100%" }}>
              {ruleFields.map(({ key, name }) => <Card size="small" key={key} extra={<Button danger type="text" onClick={() => remove(name)}>Xóa</Button>}>
                <Form.Item noStyle shouldUpdate={(prev, next) => prev.dataType !== next.dataType}>{({ getFieldValue }) => {
                  const type = getFieldValue("dataType") as FieldDataType;
                  const allowed = validationRulesByType[type] ?? [];
                  return <Form.Item name={[name, "ruleType"]} label="Điều kiện kiểm tra" rules={[{ required: true }]}>
                    <Select options={validationRuleOptions.filter((option) => allowed.includes(option.value))} placeholder={allowed.length ? "Chọn điều kiện phù hợp" : "Kiểu dữ liệu này không cần điều kiện bổ sung"} disabled={!allowed.length} />
                  </Form.Item>;
                }}</Form.Item>
                <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => {
                  const ruleType = getFieldValue(["validations", name, "ruleType"]);
                  if (ruleType === "DATE_RANGE") return <Row gutter={10}><Col span={12}><Form.Item name={[name, "min"]} label="Từ ngày"><Input type="date" /></Form.Item></Col><Col span={12}><Form.Item name={[name, "max"]} label="Đến ngày"><Input type="date" /></Form.Item></Col></Row>;
                  if (ruleType === "DECIMAL_SCALE") return <Form.Item name={[name, "scale"]} label="Số chữ số thập phân"><InputNumber min={0} max={20} style={{ width: "100%" }} /></Form.Item>;
                  if (ruleType === "EMAIL") return null;
                  return <Form.Item name={[name, "value"]} label={ruleType === "REGEX" ? "Mẫu định dạng" : "Giá trị giới hạn"}><Input /></Form.Item>;
                }}</Form.Item>
                <Form.Item name={[name, "errorMessage"]} label="Thông báo khi nhập sai"><Input /></Form.Item>
              </Card>)}
              <Button block type="dashed" icon={<PlusOutlined />} onClick={() => add()}>Thêm điều kiện kiểm tra</Button>
            </Space>}</Form.List>
            </> },
            { key: "advanced", label: "Nâng cao", children: <>
              <Alert type="info" showIcon message="Các thông tin dưới đây được hệ thống tự tạo. Chỉ thay đổi khi bạn hiểu rõ cấu trúc dữ liệu." />
              <Row gutter={12}>
                <Col span={12}><Form.Item name="fieldCode" label="Mã kỹ thuật" rules={[{ pattern: /^[a-z][A-Za-z0-9]*$/, message: "Dùng chữ không dấu, viết liền và bắt đầu bằng chữ thường" }]} extra="Để trống để hệ thống tự tạo."><Input placeholder="customerName" /></Form.Item></Col>
                <Col span={12}><Form.Item name="columnName" label="Tên cột lưu trữ" extra="Để trống để hệ thống tự tạo."><Input placeholder="customer_name" /></Form.Item></Col>
              </Row>
              <Form.Item name="componentType" label="Kiểu điều khiển hiển thị"><Select options={componentOptions} /></Form.Item>
              <div className="checkbox-grid">{[
                ["indexed", "Tạo chỉ mục dữ liệu"], ["sortable", "Cho phép sắp xếp"], ["exportable", "Cho phép xuất Excel"], ["readOnly", "Chỉ được xem"], ["hidden", "Ẩn khỏi biểu mẫu"],
              ].map(([name, label]) => <Form.Item key={name} name={name} valuePropName="checked"><Checkbox>{label}</Checkbox></Form.Item>)}</div>
            </> },
          ]} />
        </Form>
      </Drawer>

      <Modal title="Chi tiết kỹ thuật" width={860} open={ddlOpen} onCancel={() => setDdlOpen(false)} footer={<Button onClick={() => setDdlOpen(false)}>Đóng</Button>}>
        {currentVersion?.physicalTable && <div className="physical-table"><small>Tên vùng lưu trữ</small><code>{currentVersion.physicalTable}</code></div>}
        {ddlQuery.isLoading ? <Spin /> : ddlQuery.isError ? <Alert type="error" message={getErrorMessage(ddlQuery.error)} /> : <Typography.Paragraph><pre className="ddl-preview">{ddlQuery.data || "-- Chưa có thông tin cấu trúc dữ liệu"}</pre></Typography.Paragraph>}
      </Modal>
    </div>
  );
}
