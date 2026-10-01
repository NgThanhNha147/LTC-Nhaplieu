import {
  ArrowLeftOutlined, CheckCircleOutlined, ClockCircleOutlined, DeleteOutlined, EditOutlined,
  DownloadOutlined, EyeOutlined, FileAddOutlined, InboxOutlined, ReloadOutlined, SearchOutlined,
  TeamOutlined, UndoOutlined, WarningOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Card, DatePicker, Empty, Input, Modal, Progress, Select, Space, Table, Tabs, Tag, Tooltip, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { securityAdminApi } from "../api/auth";
import { batchApi, workItemApi } from "../api/batches";
import { getErrorMessage } from "../api/client";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";
import { PageError, PageLoading } from "../components/ApiState";
import { WorkflowStatusTag } from "../components/WorkflowStatusTag";
import type { WorkItem, WorkItemStatus } from "../types";

type StatusFilter = "ALL" | WorkItemStatus | "DELETED";

export function BatchDetailPage() {
  const { batchId = "" } = useParams();
  const navigate = useNavigate();
  const { message, modal } = App.useApp();
  const { hasPermission, user, securityEnabled } = useAuth();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [assigneeOpen, setAssigneeOpen] = useState(false);
  const [assignee, setAssignee] = useState<string>();
  const [assignmentReason, setAssignmentReason] = useState("");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);

  const batchQuery = useQuery({ queryKey: ["batch", batchId], queryFn: () => batchApi.get(batchId), enabled: Boolean(batchId) });
  const itemsQuery = useQuery({
    queryKey: ["work-items", batchId, status, appliedKeyword, range?.[0]?.format("YYYY-MM-DD"), range?.[1]?.format("YYYY-MM-DD")],
    queryFn: () => workItemApi.list({
      batchId,
      status: status !== "ALL" && status !== "DELETED" ? status : undefined,
      deleted: status === "DELETED" ? true : false,
      keyword: appliedKeyword || undefined,
      fromDate: range?.[0]?.format("YYYY-MM-DD"),
      toDate: range?.[1]?.format("YYYY-MM-DD"),
      page: 0,
      size: 500,
    }),
    enabled: Boolean(batchId),
  });
  const usersQuery = useQuery({ queryKey: ["users", "batch-assign"], queryFn: () => securityAdminApi.users({ page: 0, size: 100 }), enabled: assigneeOpen });
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["batch", batchId] });
    void queryClient.invalidateQueries({ queryKey: ["work-items", batchId] });
    void queryClient.invalidateQueries({ queryKey: ["batches"] });
  };
  const assignMutation = useMutation({ mutationFn: () => batchApi.assign(batchId, assignee!, batch?.rowVersion ?? 0, assignmentReason.trim()), onSuccess: () => { message.success("Đã cập nhật người phụ trách đợt"); setAssigneeOpen(false); setAssignmentReason(""); invalidate(); }, onError: (error) => message.error(getErrorMessage(error)) });
  const archiveMutation = useMutation({ mutationFn: () => batchApi.archive(batchId, batch?.rowVersion ?? 0), onSuccess: () => { message.success("Đã lưu trữ đợt hồ sơ"); invalidate(); }, onError: (error) => message.error(getErrorMessage(error)) });
  const restoreMutation = useMutation({ mutationFn: (item: WorkItem) => workItemApi.restore(item.id, item.rowVersion ?? 0), onSuccess: () => { message.success("Đã khôi phục hồ sơ"); invalidate(); }, onError: (error) => message.error(getErrorMessage(error)) });
  const deleteMutation = useMutation({ mutationFn: (item: WorkItem) => workItemApi.remove(item.id, item.rowVersion), onSuccess: () => { message.success("Đã chuyển hồ sơ vào Thùng rác"); invalidate(); }, onError: (error) => message.error(getErrorMessage(error)) });
  const uploadMutation = useMutation({
    mutationFn: () => batchApi.upload(batchId, uploadFiles, "MULTI_FILE"),
    onSuccess: (result) => {
      if (result.rejected > 0 || result.duplicates > 0) {
        message.warning(`Đã nhận ${result.accepted} file; bỏ qua ${result.duplicates} file trùng và ${result.rejected} file lỗi.`);
      } else {
        message.success(`Đã thêm ${result.accepted} tài liệu vào đợt`);
      }
      setUploadFiles([]);
      setUploadOpen(false);
      invalidate();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const exportMutation = useMutation({
    mutationFn: () => batchApi.export(batchId, {
      status: status !== "ALL" && status !== "DELETED" ? status : undefined,
      fromDate: range?.[0]?.format("YYYY-MM-DD"),
      toDate: range?.[1]?.format("YYYY-MM-DD"),
    }),
    onSuccess: () => message.success("Đã tạo file Excel theo bộ lọc hiện tại"),
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const batch = batchQuery.data;
  const counters = batch?.counters;
  const total = counters?.total ?? 0;
  const progress = total ? Math.round(((counters?.approved ?? 0) / total) * 100) : 0;
  const tabItems = useMemo(() => [
    { key: "ALL", label: <>Tất cả <b>{total}</b></> },
    { key: "UNPROCESSED", label: <>Chờ nhập <b>{counters?.unprocessed ?? 0}</b></> },
    { key: "DRAFT", label: <>Nháp <b>{counters?.draft ?? 0}</b></> },
    { key: "PENDING_APPROVAL", label: <>Chờ duyệt <b>{counters?.pendingApproval ?? 0}</b></> },
    { key: "REJECTED", label: <>Bị trả lại <b>{counters?.rejected ?? 0}</b></> },
    { key: "APPROVED", label: <>Đã duyệt <b>{counters?.approved ?? 0}</b></> },
    { key: "DELETED", label: <>Thùng rác <b>{counters?.deleted ?? 0}</b></> },
  ], [counters, total]);

  const columns: ColumnsType<WorkItem> = [
    { title: "STT", dataIndex: "sequenceNo", width: 76, align: "center", render: (value: number) => <span className="sequence-number">{value}</span> },
    { title: "Tài liệu", key: "document", minWidth: 280, render: (_, item) => <div className="work-item-file"><strong>{item.documentName}</strong><small>{item.relativePath && item.relativePath !== item.documentName ? item.relativePath : item.batchCode}</small></div> },
    { title: "Trạng thái", dataIndex: "status", width: 145, render: (value: WorkItemStatus) => <WorkflowStatusTag status={value} /> },
    { title: "Người nhập", key: "assignee", width: 180, render: (_, item) => item.assignedDisplayName || item.assignedUsername || <span className="muted-cell">Chưa giao</span> },
    { title: "Cập nhật", dataIndex: "updatedAt", width: 165, render: (value?: string) => value ? new Date(value).toLocaleString("vi-VN") : "—" },
    { title: "Ghi chú", key: "note", width: 220, ellipsis: true, render: (_, item) => item.status === "REJECTED" ? <Tooltip title={item.rejectionReason}><span className="rejection-cell"><WarningOutlined /> {item.rejectionReason}</span></Tooltip> : "—" },
    {
      title: "", key: "action", fixed: "right", width: 116, align: "right",
      render: (_, item) => {
        const isMine = !securityEnabled || item.assignedUserId === user?.id;
        const canEditItem = isMine && ["UNPROCESSED", "DRAFT", "REJECTED"].includes(item.status) && hasPermission(Permissions.RECORD_UPDATE);
        const canDeleteItem = isMine && hasPermission(Permissions.RECORD_DELETE) && item.status !== "APPROVED";
        const openLabel = canEditItem ? "Mở nhập liệu" : item.status === "PENDING_APPROVAL" ? "Kiểm tra hồ sơ" : "Xem hồ sơ (chỉ đọc)";
        return <Space size={2} onClick={(event) => event.stopPropagation()}>
          {status === "DELETED" ? hasPermission(Permissions.RECORD_RESTORE) && <Tooltip title="Khôi phục"><Button type="text" icon={<UndoOutlined />} onClick={() => restoreMutation.mutate(item)} /></Tooltip> : <>
            <Tooltip title={openLabel}><Button type="text" icon={canEditItem ? <EditOutlined /> : <EyeOutlined />} onClick={() => navigate(`/work-items/${item.id}`)} /></Tooltip>
            {canDeleteItem && <Tooltip title="Chuyển vào Thùng rác"><Button danger type="text" icon={<DeleteOutlined />} onClick={() => modal.confirm({ title: "Chuyển hồ sơ vào Thùng rác?", content: "Hồ sơ có thể được quản trị viên khôi phục sau.", okText: "Chuyển vào Thùng rác", okButtonProps: { danger: true }, onOk: () => deleteMutation.mutateAsync(item) })} /></Tooltip>}
          </>}
        </Space>;
      },
    },
  ];

  if (batchQuery.isLoading) return <PageLoading />;
  if (batchQuery.isError || !batch) return <PageError message={getErrorMessage(batchQuery.error)} onRetry={() => void batchQuery.refetch()} />;

  return <div className="page-stack batch-detail-page">
    <Card className="batch-overview-card">
      <div className="batch-overview-top">
        <Button type="text" aria-label="Về mẫu hồ sơ" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/workspaces/${batch.templateCode}?tab=batches`)} />
        <div className="batch-overview-title"><strong>{batch.batchName}</strong><span>{batch.batchCode} · {batch.templateName}{batch.templateVersion ? ` · v${batch.templateVersion}` : ""}</span></div>
        <div className="batch-overview-actions">
          {hasPermission(Permissions.RECORD_EXPORT) && <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>Xuất Excel</Button>}
          {hasPermission(Permissions.BATCH_ARCHIVE) && !batch.archivedAt && (total === 0 || counters?.approved === total) && <Button icon={<InboxOutlined />} loading={archiveMutation.isPending} onClick={() => modal.confirm({ title: "Lưu trữ đợt hồ sơ?", content: "Đợt đã lưu trữ sẽ khóa tải thêm tài liệu và chuyển giao công việc.", okText: "Lưu trữ", onOk: () => archiveMutation.mutateAsync() })}>Lưu trữ đợt</Button>}
          {hasPermission(Permissions.BATCH_ASSIGN) && !batch.archivedAt && <Button icon={<TeamOutlined />} onClick={() => { setAssignee(batch.assignedUserId); setAssignmentReason(""); setAssigneeOpen(true); }}>Giao việc</Button>}
          {hasPermission(Permissions.BATCH_UPLOAD) && !batch.archivedAt && <Button icon={<FileAddOutlined />} onClick={() => setUploadOpen(true)}>Thêm tài liệu</Button>}
        </div>
      </div>
      <div className="batch-overview-progress"><Progress percent={progress} strokeColor="#009edb" /><div><span><CheckCircleOutlined /> {counters?.approved ?? 0} đã duyệt</span><span><ClockCircleOutlined /> {counters?.pendingApproval ?? 0} chờ duyệt</span><span><InboxOutlined /> {counters?.unprocessed ?? 0} chờ nhập</span><span><WarningOutlined /> {counters?.rejected ?? 0} bị trả lại</span></div></div>
    </Card>

    <Card className="batch-work-card" styles={{ body: { padding: 0 } }}>
      <Tabs className="workflow-tabs" activeKey={status} items={tabItems} onChange={(key) => setStatus(key as StatusFilter)} />
      <div className="work-item-filters">
        <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên file hoặc đường dẫn" value={keyword} onChange={(event) => setKeyword(event.target.value)} onPressEnter={() => setAppliedKeyword(keyword.trim())} />
        <DatePicker.RangePicker value={range} format="DD-MM-YYYY" placeholder={["Từ ngày", "Đến ngày"]} onChange={(value) => setRange(value as typeof range)} />
        <Button type="primary" icon={<SearchOutlined />} onClick={() => setAppliedKeyword(keyword.trim())}>Tìm</Button>
        {(appliedKeyword || range) && <Button icon={<ReloadOutlined />} onClick={() => { setKeyword(""); setAppliedKeyword(""); setRange(null); }}>Đặt lại</Button>}
      </div>
      <Table rowKey="id" loading={itemsQuery.isLoading} columns={columns} dataSource={itemsQuery.data?.items ?? []} scroll={{ x: 1200 }} onRow={(item) => status === "DELETED" ? {} : ({ onClick: () => navigate(`/work-items/${item.id}`), style: { cursor: "pointer" } })}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (count) => `${count} hồ sơ` }}
        locale={{ emptyText: <Empty description="Không có hồ sơ trong nhóm này" /> }} />
    </Card>

    <Modal title="Giao đợt hồ sơ" open={assigneeOpen} onCancel={() => setAssigneeOpen(false)} onOk={() => assignMutation.mutate()} okText="Lưu người phụ trách" okButtonProps={{ disabled: !assignee || assignmentReason.trim().length < 5 }} confirmLoading={assignMutation.isPending}>
      <p>Chỉ tài khoản nhập liệu mới được chọn. Không thể chuyển giao khi đợt có hồ sơ chờ duyệt hoặc đã duyệt.</p>
      <Select style={{ width: "100%" }} showSearch optionFilterProp="label" value={assignee} onChange={setAssignee} placeholder="Chọn người nhập liệu" options={(usersQuery.data?.items ?? []).filter((user) => user.status === "ACTIVE" && user.functions?.includes(Permissions.RECORD_UPDATE) && user.functions?.includes(Permissions.RECORD_SUBMIT)).map((user) => ({ value: user.id, label: `${user.displayName} (${user.username})` }))} />
      <Input.TextArea style={{ marginTop: 12 }} rows={3} maxLength={2000} showCount value={assignmentReason} onChange={(event) => setAssignmentReason(event.target.value)} placeholder="Nhập lý do giao hoặc chuyển người phụ trách" />
    </Modal>

    <Modal title="Thêm tài liệu vào đợt" open={uploadOpen} onCancel={() => { setUploadOpen(false); setUploadFiles([]); }} onOk={() => uploadMutation.mutate()} okText="Tải lên" okButtonProps={{ disabled: uploadFiles.length === 0 }} confirmLoading={uploadMutation.isPending}>
      <Upload.Dragger multiple accept="application/pdf,.pdf" fileList={uploadFiles.map((file) => ({ uid: `${file.name}-${file.lastModified}`, name: file.name, status: "done" as const }))} beforeUpload={(file) => { setUploadFiles((current) => [...current, file]); return false; }} onRemove={(file) => { setUploadFiles((current) => current.filter((item) => `${item.name}-${item.lastModified}` !== file.uid)); return true; }}>
        <p className="ant-upload-drag-icon"><FileAddOutlined /></p><p className="ant-upload-text">Chọn hoặc thả nhiều file PDF vào đây</p><p className="ant-upload-hint">Mỗi PDF tối đa 50 MB; tổng một lần tải tối đa 1 GB. File trùng nội dung sẽ được bỏ qua.</p>
      </Upload.Dragger>
    </Modal>
  </div>;
}
