import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  FileSearchOutlined,
  InboxOutlined,
  PlusOutlined,
  SaveOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  Checkbox,
  Input,
  Modal,
  Progress,
  Select,
  Space,
  Spin,
  Tag,
  Upload,
} from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { documentAnalysisApi } from "../api/documentAnalysis";
import { getErrorMessage } from "../api/client";
import { documentApi } from "../api/documents";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";
import { DocumentViewer } from "../components/DocumentViewer";
import type {
  ComponentType,
  DocumentAnalysisResult,
  FieldDataType,
  SuggestedDocumentField,
  SuggestedDocumentForm,
  SuggestedDocumentGroup,
} from "../types";

const dataTypeOptions: Array<{ value: FieldDataType; label: string }> = [
  { value: "STRING", label: "Văn bản ngắn" },
  { value: "TEXTAREA", label: "Văn bản dài" },
  { value: "INTEGER", label: "Số nguyên" },
  { value: "DECIMAL", label: "Số thập phân" },
  { value: "DATE", label: "Ngày" },
  { value: "DATETIME", label: "Ngày và giờ" },
  { value: "BOOLEAN", label: "Có / Không" },
  { value: "LIST", label: "Danh sách" },
  { value: "EMAIL", label: "Email" },
  { value: "PHONE", label: "Số điện thoại" },
];

function componentFor(type: FieldDataType): ComponentType {
  if (type === "TEXTAREA") return "TEXTAREA";
  if (type === "INTEGER" || type === "DECIMAL") return "NUMBER";
  if (type === "DATE") return "DATE";
  if (type === "DATETIME") return "DATETIME";
  if (type === "BOOLEAN") return "CHECKBOX";
  if (type === "LIST") return "SELECT";
  if (type === "EMAIL") return "EMAIL";
  if (type === "PHONE") return "PHONE";
  return "TEXT";
}

function confidenceLabel(value?: number) {
  if ((value ?? 0) >= 0.85) return { color: "success", text: "Tin cậy cao" };
  if ((value ?? 0) >= 0.6) return { color: "warning", text: "Cần kiểm tra" };
  return { color: "error", text: "Chưa xác định rõ" };
}

export function DocumentIntakePage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const [file, setFile] = useState<File>();
  const [analysis, setAnalysis] = useState<DocumentAnalysisResult>();
  const [form, setForm] = useState<SuggestedDocumentForm>({ groups: [] });
  const [processing, setProcessing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [promoteOpen, setPromoteOpen] = useState(false);
  const [promoting, setPromoting] = useState(false);
  const [templateCode, setTemplateCode] = useState("");
  const [templateName, setTemplateName] = useState("");
  const canPromote = hasPermission(Permissions.TEMPLATE_CREATE);

  const selectedCount = useMemo(() => form.groups.reduce(
    (total, group) => total + group.fields.filter((field) => field.selected).length, 0,
  ), [form]);

  const startAnalysis = async () => {
    if (!file) return;
    setProcessing(true);
    try {
      const uploaded = await documentApi.upload(file);
      const result = await documentAnalysisApi.analyze(uploaded.id);
      setAnalysis(result);
      setForm(result.reviewedForm ?? result.suggestedForm);
      setTemplateName(result.documentType && result.documentType !== "Tài liệu chưa xác định" ? result.documentType : file.name.replace(/\.pdf$/i, ""));
      message.success("Đã phân tích và đề xuất các trường nhập liệu");
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setProcessing(false);
    }
  };

  const updateGroup = (groupIndex: number, patch: Partial<SuggestedDocumentGroup>) => {
    setForm((current) => ({
      groups: current.groups.map((group, index) => index === groupIndex ? { ...group, ...patch } : group),
    }));
  };

  const updateField = (groupIndex: number, fieldIndex: number, patch: Partial<SuggestedDocumentField>) => {
    setForm((current) => ({
      groups: current.groups.map((group, index) => index !== groupIndex ? group : {
        ...group,
        fields: group.fields.map((field, position) => position === fieldIndex ? { ...field, ...patch } : field),
      }),
    }));
  };

  const removeField = (groupIndex: number, fieldIndex: number) => {
    setForm((current) => ({
      groups: current.groups.map((group, index) => index !== groupIndex ? group : {
        ...group,
        fields: group.fields.filter((_, position) => position !== fieldIndex),
      }).filter((group) => group.fields.length > 0),
    }));
  };

  const addField = (groupIndex: number) => {
    const suffix = Date.now().toString().slice(-6);
    const field: SuggestedDocumentField = {
      id: crypto.randomUUID(),
      fieldCode: `truong_bo_sung_${suffix}`,
      label: "Trường bổ sung",
      dataType: "STRING",
      componentType: "TEXT",
      confidence: 1,
      required: false,
      selected: true,
    };
    setForm((current) => ({
      groups: current.groups.map((group, index) => index === groupIndex
        ? { ...group, fields: [...group.fields, field] }
        : group),
    }));
  };

  const addGroup = () => {
    const suffix = form.groups.length + 1;
    const fieldSuffix = Date.now().toString().slice(-6);
    setForm((current) => ({ groups: [...current.groups, {
      code: `NHOM_BO_SUNG_${suffix}`,
      label: `Nhóm bổ sung ${suffix}`,
      displayOrder: current.groups.length,
      fields: [{
        id: crypto.randomUUID(),
        fieldCode: `truong_bo_sung_${fieldSuffix}`,
        label: "Trường bổ sung",
        dataType: "STRING",
        componentType: "TEXT",
        confidence: 1,
        required: false,
        selected: true,
      }],
    }] }));
  };

  const saveReview = async () => {
    if (!analysis) return;
    setSaving(true);
    try {
      const updated = await documentAnalysisApi.review(analysis.id, form, analysis.rowVersion);
      setAnalysis(updated);
      setForm(updated.reviewedForm ?? updated.suggestedForm);
      message.success("Đã lưu biểu mẫu và dữ liệu tạm cho tài liệu này");
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const promote = async () => {
    if (!analysis || !templateCode.trim() || !templateName.trim()) return;
    setPromoting(true);
    try {
      let current = analysis;
      if (current.status !== "REVIEWED") {
        current = await documentAnalysisApi.review(current.id, form, current.rowVersion);
        setAnalysis(current);
      }
      const result = await documentAnalysisApi.promote(current.id, {
        templateCode: templateCode.trim(),
        templateName: templateName.trim(),
        description: `Biểu mẫu được tạo từ tài liệu ${analysis.documentName}`,
        rowVersion: current.rowVersion,
      });
      message.success(`Đã tạo biểu mẫu ${result.templateName}`);
      setPromoteOpen(false);
      navigate(`/templates/${result.templateId}/designer`);
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setPromoting(false);
    }
  };

  const reset = () => {
    setFile(undefined);
    setAnalysis(undefined);
    setForm({ groups: [] });
    setTemplateCode("");
    setTemplateName("");
  };

  if (!analysis) {
    return <div className="document-intake-page">
      <section className="document-intake-start">
        <div className="document-intake-heading">
          <FileSearchOutlined />
          <div>
            <h1>Phân tích tài liệu mới</h1>
            <p>Tải PDF lên để hệ thống đọc nội dung, suy luận ý nghĩa và đề xuất các ô nhập liệu.</p>
          </div>
        </div>
        <Upload.Dragger
          accept=".pdf,application/pdf"
          maxCount={1}
          fileList={file ? [{ uid: "selected", name: file.name, status: "done", originFileObj: file } as never] : []}
          beforeUpload={(selected) => { setFile(selected); return false; }}
          onRemove={() => { setFile(undefined); return true; }}
          disabled={processing}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">Thả một file PDF vào đây hoặc bấm để chọn</p>
          <p className="ant-upload-hint">Có thể sử dụng PDF điện tử hoặc bản scan rõ nét.</p>
        </Upload.Dragger>
        <Button type="primary" size="large" icon={<FileSearchOutlined />} disabled={!file} loading={processing} onClick={() => void startAnalysis()}>
          Phân tích và đề xuất biểu mẫu
        </Button>
        {processing && <div className="analysis-processing"><Spin /><span>Hệ thống đang đọc nội dung và xác định các trường dữ liệu…</span></div>}
      </section>
    </div>;
  }

  return <div className="document-analysis-page">
    <div className="document-analysis-command">
      <Button onClick={reset}>Tài liệu khác</Button>
      <div className="document-analysis-summary">
        <strong>{analysis.documentType || "Tài liệu chưa xác định"}</strong>
        <span>{selectedCount} trường được chọn · {analysis.documentName}</span>
      </div>
      <Space>
        <Button icon={<SaveOutlined />} loading={saving} onClick={() => void saveReview()}>Lưu kết quả</Button>
        {canPromote && <Button type="primary" icon={<ArrowRightOutlined />} onClick={() => setPromoteOpen(true)}>Lưu thành mẫu dùng lại</Button>}
      </Space>
    </div>

    {analysis.templateMatch && <Alert
      className="analysis-template-match"
      type="success"
      showIcon
      message={`Tài liệu có thể thuộc mẫu “${analysis.templateMatch.templateName}”`}
      description={`Mức độ phù hợp ${Math.round(analysis.templateMatch.confidence * 100)}%. Người dùng vẫn có thể kiểm tra và chỉnh lại các trường bên dưới.`}
    />}

    <div className="document-analysis-workspace">
      <DocumentViewer initialUrl={analysis.documentUrl} readOnly />
      <main className="analysis-review-pane">
        <div className="analysis-review-intro">
          <div><strong>Kiểm tra biểu mẫu đề xuất</strong><small>Đổi tên, kiểu dữ liệu hoặc giá trị nếu hệ thống suy luận chưa đúng.</small></div>
          <Button icon={<PlusOutlined />} onClick={addGroup}>Thêm nhóm</Button>
        </div>

        {form.groups.map((group, groupIndex) => <section className="analysis-group" key={`${group.code}-${groupIndex}`}>
          <div className="analysis-group-heading">
            <Input value={group.label} onChange={(event) => updateGroup(groupIndex, { label: event.target.value })} />
            <Input value={group.code} onChange={(event) => updateGroup(groupIndex, { code: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, "_") })} />
            <Button type="text" icon={<PlusOutlined />} onClick={() => addField(groupIndex)}>Thêm trường</Button>
          </div>
          <div className="analysis-field-list">
            {group.fields.map((field, fieldIndex) => {
              const confidence = confidenceLabel(field.confidence);
              return <div className={`analysis-field-row ${field.selected ? "" : "is-ignored"}`} key={field.id}>
                <Checkbox checked={field.selected} onChange={(event) => updateField(groupIndex, fieldIndex, { selected: event.target.checked })} aria-label={`Chọn trường ${field.label}`} />
                <div className="analysis-field-main">
                  <Input value={field.label} onChange={(event) => updateField(groupIndex, fieldIndex, { label: event.target.value })} placeholder="Tên trường hiển thị" />
                  <Input value={field.fieldCode} onChange={(event) => updateField(groupIndex, fieldIndex, { fieldCode: event.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_") })} placeholder="Mã trường" />
                </div>
                <Select
                  value={field.dataType}
                  options={dataTypeOptions}
                  onChange={(dataType: FieldDataType) => updateField(groupIndex, fieldIndex, { dataType, componentType: componentFor(dataType) })}
                />
                <Input.TextArea autoSize={{ minRows: 1, maxRows: 3 }} value={field.value} onChange={(event) => updateField(groupIndex, fieldIndex, { value: event.target.value })} placeholder="Giá trị nhận diện hoặc nhập bổ sung" />
                <div className="analysis-field-evidence">
                  <Tag color={confidence.color}>{confidence.text}</Tag>
                  <small>{field.page ? `Trang ${field.page}` : "Không xác định trang"}</small>
                  {field.evidence && <span title={field.evidence}>{field.evidence}</span>}
                </div>
                <Button danger type="text" icon={<DeleteOutlined />} aria-label={`Xóa trường ${field.label}`} onClick={() => removeField(groupIndex, fieldIndex)} />
              </div>;
            })}
          </div>
        </section>)}

        {analysis.status === "REVIEWED" && <div className="analysis-saved-state"><CheckCircleOutlined /> Biểu mẫu tạm đã được lưu cho tài liệu này.</div>}
      </main>
    </div>

    <Modal
      title="Lưu thành biểu mẫu dùng lại"
      open={promoteOpen}
      onCancel={() => setPromoteOpen(false)}
      onOk={() => void promote()}
      confirmLoading={promoting}
      okText="Tạo biểu mẫu nháp"
      okButtonProps={{ disabled: !templateCode.trim() || !templateName.trim() }}
    >
      <p>Hệ thống sẽ tạo một biểu mẫu nháp từ các trường đang được chọn. Bạn có thể kiểm tra rule và trường định danh trước khi phát hành.</p>
      <label className="analysis-modal-field"><span>Tên biểu mẫu</span><Input value={templateName} onChange={(event) => setTemplateName(event.target.value)} /></label>
      <label className="analysis-modal-field"><span>Mã biểu mẫu</span><Input value={templateCode} onChange={(event) => setTemplateCode(event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, "_"))} placeholder="VD: HOP_DONG_CHUYEN_NHUONG" /></label>
    </Modal>
  </div>;
}
