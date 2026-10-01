import {
  AppstoreAddOutlined, CheckCircleOutlined, ClockCircleOutlined, CloudUploadOutlined,
  EditOutlined, EyeOutlined, FileZipOutlined, FolderOpenOutlined, InboxOutlined,
  PlusOutlined, SearchOutlined, TeamOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  App, Button, Empty, Form, Input, Modal, Progress, Select, Space, Table, Tag, Tooltip, Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { batchApi, type CreateBatchPayload } from "../api/batches";
import { getErrorMessage } from "../api/client";
import { securityAdminApi } from "../api/auth";
import { templateApi } from "../api/templates";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";
import { PageError, PageLoading } from "../components/ApiState";
import type { BatchSourceType, IngestionBatch, TemplateSummary } from "../types";

interface BatchFormValues {
  batchName: string;
  templateVersionId?: string;
  assignedTo?: string;
  description?: string;
}

function batchProgress(batch: IngestionBatch) {
  const total = batch.counters?.total ?? 0;
  return total ? Math.round(((batch.counters?.approved ?? 0) / total) * 100) : 0;
}

interface BatchesPageProps {
  mine?: boolean;
  template?: TemplateSummary;
  startCreate?: boolean;
}

export function BatchesPage({ mine = false, template, startCreate = false }: BatchesPageProps) {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { hasPermission, user } = useAuth();
  const queryClient = useQueryClient();
  const folderInput = useRef<HTMLInputElement>(null);
  const multiInput = useRef<HTMLInputElement>(null);
  const [form] = Form.useForm<BatchFormValues>();
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [createOpen, setCreateOpen] = useState(startCreate && hasPermission(Permissions.BATCH_CREATE));
  const [files, setFiles] = useState<File[]>([]);
  const [sourceType, setSourceType] = useState<BatchSourceType>("MULTI_FILE");
  const selfServiceEntry = hasPermission(Permissions.BATCH_CREATE) && !hasPermission(Permissions.BATCH_ASSIGN);

  const batchesQuery = useQuery({
    queryKey: ["batches", mine, template?.id, appliedKeyword],
    queryFn: () => batchApi.list({ templateId: template?.id, assignedUserId: mine ? user?.id : undefined, keyword: appliedKeyword || undefined, page: 0, size: 100 }),
  });
  const templatesQuery = useQuery({ queryKey: ["templates", "batch-create"], queryFn: () => templateApi.list(0, 100), enabled: createOpen && !template });
  const usersQuery = useQuery({
    queryKey: ["users", "batch-assign"],
    queryFn: () => securityAdminApi.users({ page: 0, size: 100 }),
    enabled: createOpen && hasPermission(Permissions.BATCH_ASSIGN),
  });
  const createMutation = useMutation({
    mutationFn: async (values: BatchFormValues) => {
      const templateVersionId = template?.currentVersionId ?? values.templateVersionId;
      if (!templateVersionId) throw new Error("Mẫu hồ sơ chưa có phiên bản đang sử dụng.");
      const payload: CreateBatchPayload = {
        batchName: values.batchName,
        templateVersionId,
        assignedUserId: values.assignedTo,
        description: values.description,
      };
      const batch = await batchApi.create(payload);
      const upload = files.length && hasPermission(Permissions.BATCH_UPLOAD)
        ? await batchApi.upload(batch.id, files, sourceType)
        : undefined;
      return { batch: upload?.batch ?? batch, upload };
    },
    onSuccess: ({ batch, upload }) => {
      if (upload && (upload.rejected > 0 || upload.duplicates > 0)) {
        message.warning(`Đã nhận ${upload.accepted} file; bỏ qua ${upload.duplicates} file trùng và ${upload.rejected} file lỗi.`);
      } else {
        message.success(upload ? `Đã tạo đợt và tiếp nhận ${upload.accepted} tài liệu` : "Đã tạo đợt hồ sơ");
      }
      setCreateOpen(false);
      setFiles([]);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ["batches"] });
      navigate(template ? `/workspaces/${template.code}/batches/${batch.id}` : `/batches/${batch.id}`);
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const selectFiles = (selected: FileList | null, type: BatchSourceType) => {
    if (!selected?.length) return;
    const next = Array.from(selected);
    setFiles(next);
    setSourceType(type);
  };

  const columns: ColumnsType<IngestionBatch> = [
    {
      title: "Đợt hồ sơ", key: "batch", width: 300,
      render: (_, batch) => <div className="batch-main-cell"><strong>{batch.batchName}</strong><span>{batch.batchCode}{template ? ` · v${batch.templateVersion ?? "—"}` : ` · ${batch.templateName}`}</span></div>,
    },
    {
      title: "Tiến độ", key: "progress", width: 220,
      render: (_, batch) => <div className="batch-progress-cell"><Progress percent={batchProgress(batch)} size="small" strokeColor="#009edb" /><small>{batch.counters?.approved ?? 0}/{batch.counters?.total ?? 0} hồ sơ đã duyệt</small></div>,
    },
    {
      title: "Đang xử lý", key: "active", width: 170,
      render: (_, batch) => <Space size={4} wrap><Tag icon={<InboxOutlined />}>{batch.counters?.unprocessed ?? 0}</Tag><Tag color="blue" icon={<EditOutlined />}>{batch.counters?.draft ?? 0}</Tag><Tag color="gold" icon={<ClockCircleOutlined />}>{batch.counters?.pendingApproval ?? 0}</Tag></Space>,
    },
    { title: "Người thực hiện", key: "assignee", width: 180, render: (_, batch) => batch.assignedDisplayName || batch.assignedUsername || <span className="muted-cell">Chưa giao</span> },
    { title: "Ngày tạo", dataIndex: "createdAt", width: 160, render: (value?: string) => value ? new Date(value).toLocaleString("vi-VN") : "—" },
    { title: "", key: "action", width: 70, fixed: "right", align: "center", render: (_, batch) => <Tooltip title="Mở đợt hồ sơ"><Button type="text" icon={<EyeOutlined />} onClick={() => navigate(template ? `/workspaces/${template.code}/batches/${batch.id}` : `/batches/${batch.id}`)} /></Tooltip> },
  ];

  if (batchesQuery.isLoading) return <PageLoading />;
  if (batchesQuery.isError) return <PageError message={getErrorMessage(batchesQuery.error)} onRetry={() => void batchesQuery.refetch()} />;

  return <div className="batch-page batch-page-inline">
    <div className="batch-command-section">
      <div className="batch-command-row">
        <div><strong>{mine ? "Đợt hồ sơ của tôi" : template ? `Đợt hồ sơ của ${template.name}` : "Quản lý đợt hồ sơ"}</strong><small>{mine ? "Theo dõi tiến độ và tiếp tục hồ sơ đang làm." : selfServiceEntry ? "Tạo đợt, tải tài liệu và bắt đầu nhập dữ liệu của bạn." : "Tiếp nhận tài liệu, giao việc và theo dõi tiến độ từng đợt."}</small></div>
        <div className="batch-command-actions">
          <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên hoặc mã đợt" value={keyword} onChange={(event) => setKeyword(event.target.value)} onPressEnter={() => setAppliedKeyword(keyword.trim())} />
          <Button icon={<SearchOutlined />} onClick={() => setAppliedKeyword(keyword.trim())}>Tìm</Button>
          {!mine && hasPermission(Permissions.BATCH_CREATE) && <Button type="primary" icon={<PlusOutlined />} disabled={Boolean(template && !template.currentVersionId)} onClick={() => setCreateOpen(true)}>{selfServiceEntry ? "Tạo đợt của tôi" : "Tạo đợt"}</Button>}
        </div>
      </div>
    </div>

    <div className="batch-table-section">
      <Table rowKey="id" columns={columns} dataSource={batchesQuery.data?.items ?? []} scroll={{ x: 1100 }} onRow={(batch) => ({ onClick: () => navigate(template ? `/workspaces/${template.code}/batches/${batch.id}` : `/batches/${batch.id}`), style: { cursor: "pointer" } })}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (total) => `${total} đợt hồ sơ` }}
        locale={{ emptyText: <Empty description={mine ? "Bạn chưa được giao đợt hồ sơ nào" : "Chưa có đợt hồ sơ"} /> }} />
    </div>

    <Modal className="batch-create-modal" width={760} title={selfServiceEntry ? "Tạo đợt nhập liệu của tôi" : "Tạo đợt hồ sơ"} open={createOpen} onCancel={() => setCreateOpen(false)} okText={files.length ? "Tạo và tải tài liệu" : "Tạo đợt"} confirmLoading={createMutation.isPending} onOk={() => void form.validateFields().then((values) => createMutation.mutate(values))}>
      <Form form={form} layout="vertical" requiredMark="optional">
        <div className="batch-form-grid">
          <Form.Item name="batchName" label="Tên đợt hồ sơ" rules={[{ required: true, message: "Nhập tên đợt để dễ theo dõi" }]}><Input placeholder="Ví dụ: Hồ sơ đất đai huyện A - Tháng 10/2026" /></Form.Item>
          {template ? <div className="batch-locked-template"><span>Mẫu hồ sơ áp dụng</span><strong>{template.name}</strong><small>{template.code} · Phiên bản {template.currentVersion ?? "—"}</small></div> : <Form.Item name="templateVersionId" label="Loại hồ sơ" rules={[{ required: true, message: "Chọn biểu mẫu áp dụng cho đợt" }]}><Select loading={templatesQuery.isLoading} placeholder="Chọn biểu mẫu đã phát hành" options={(templatesQuery.data?.items ?? []).filter((item) => item.currentVersionId).map((item) => ({ value: item.currentVersionId!, label: `${item.name} · v${item.currentVersion ?? "—"}` }))} /></Form.Item>}
          {hasPermission(Permissions.BATCH_ASSIGN) && <Form.Item name="assignedTo" label="Giao cho người nhập"><Select allowClear showSearch optionFilterProp="label" loading={usersQuery.isLoading} placeholder="Có thể giao sau" options={(usersQuery.data?.items ?? []).filter((user) => user.status === "ACTIVE" && user.functions?.includes(Permissions.RECORD_UPDATE) && user.functions?.includes(Permissions.RECORD_SUBMIT)).map((user) => ({ value: user.id, label: `${user.displayName} (${user.username})` }))} /></Form.Item>}
          {selfServiceEntry && <div className="batch-locked-template"><span>Người thực hiện</span><strong>{user?.displayName || user?.username || "Tài khoản hiện tại"}</strong><small>Đợt hồ sơ sẽ tự động giao cho bạn.</small></div>}
          <Form.Item name="description" label="Ghi chú"><Input.TextArea rows={2} placeholder="Thông tin nguồn hồ sơ hoặc yêu cầu cần lưu ý" /></Form.Item>
        </div>
      </Form>
      {hasPermission(Permissions.BATCH_UPLOAD) && <div className="batch-upload-block">
        <div className="batch-upload-heading"><div><strong>Tài liệu trong đợt</strong><small>Có thể tạo đợt trống hoặc tải ZIP, thư mục hay nhiều PDF cùng lúc.</small></div>{files.length > 0 && <Button type="link" onClick={() => setFiles([])}>Bỏ danh sách</Button>}</div>
        <div className="batch-upload-options">
          <Upload accept=".zip,application/zip" maxCount={1} showUploadList={false} beforeUpload={(file) => { selectFiles({ 0: file, length: 1, item: () => file } as unknown as FileList, "ZIP"); return false; }}><Button icon={<FileZipOutlined />}>Chọn file ZIP</Button></Upload>
          <Button icon={<FolderOpenOutlined />} onClick={() => { folderInput.current?.setAttribute("webkitdirectory", ""); folderInput.current?.click(); }}>Chọn thư mục PDF</Button>
          <Button icon={<CloudUploadOutlined />} onClick={() => multiInput.current?.click()}>Chọn nhiều PDF</Button>
          <input ref={folderInput} hidden type="file" multiple accept="application/pdf,.pdf" onChange={(event) => selectFiles(event.target.files, "FOLDER")} />
          <input ref={multiInput} hidden type="file" multiple accept="application/pdf,.pdf" onChange={(event) => selectFiles(event.target.files, "MULTI_FILE")} />
        </div>
        {files.length > 0 && <div className="batch-file-summary"><AppstoreAddOutlined /><span><strong>{files.length} file đã chọn</strong><small>{sourceType === "ZIP" ? files[0]?.name : `${files.slice(0, 2).map((file) => file.webkitRelativePath || file.name).join(", ")}${files.length > 2 ? "…" : ""}`}</small></span></div>}
      </div>}
    </Modal>
  </div>;
}
