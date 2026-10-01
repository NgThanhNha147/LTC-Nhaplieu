import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  EditOutlined,
  FolderOpenOutlined,
  InboxOutlined,
} from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, Empty, Progress } from "antd";
import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { batchApi } from "../api/batches";
import { getErrorMessage } from "../api/client";
import { PageError, PageLoading } from "../components/ApiState";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";

export function OverviewPage() {
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const canAssign = hasPermission(Permissions.BATCH_ASSIGN);
  const canApprove = hasPermission(Permissions.RECORD_APPROVE);
  const batchesQuery = useQuery({
    queryKey: ["batches", "overview"],
    queryFn: () => batchApi.list({ page: 0, size: 200 }),
  });

  const batches = useMemo(() => {
    const items = batchesQuery.data?.items ?? [];
    return [...items].sort((left, right) => String(right.createdAt ?? "").localeCompare(String(left.createdAt ?? "")));
  }, [batchesQuery.data?.items]);

  if (batchesQuery.isLoading) return <PageLoading />;
  if (batchesQuery.isError) return <PageError message={getErrorMessage(batchesQuery.error)} onRetry={() => void batchesQuery.refetch()} />;

  const totals = batches.reduce((result, batch) => ({
    records: result.records + (batch.counters?.total ?? 0),
    unprocessed: result.unprocessed + (batch.counters?.unprocessed ?? 0),
    draft: result.draft + (batch.counters?.draft ?? 0),
    pending: result.pending + (batch.counters?.pendingApproval ?? 0),
    approved: result.approved + (batch.counters?.approved ?? 0),
  }), { records: 0, unprocessed: 0, draft: 0, pending: 0, approved: 0 });
  const progress = totals.records ? Math.round((totals.approved / totals.records) * 100) : 0;

  const statuses = [
    { key: "unprocessed", label: "Chờ nhập", value: totals.unprocessed, icon: <InboxOutlined />, color: "#1688bb" },
    { key: "draft", label: "Bản nháp", value: totals.draft, icon: <EditOutlined />, color: "#526ee8" },
    { key: "pending", label: "Chờ duyệt", value: totals.pending, icon: <ClockCircleOutlined />, color: "#d58a12" },
    { key: "approved", label: "Đã duyệt", value: totals.approved, icon: <CheckCircleOutlined />, color: "#2a9b68" },
  ];

  return <div className="page-stack overview-page">
    <Card className="overview-surface" styles={{ body: { padding: 0 } }}>
      <div className="overview-dashboard">
        <section className="overview-completion">
          <div className="overview-completion-copy">
            <span>{canAssign ? "Tiến độ toàn hệ thống" : "Tiến độ hoàn thành"}</span>
            <strong>{totals.approved}<small> / {totals.records} hồ sơ</small></strong>
            <p>{canAssign ? "Tổng hợp các đợt để quản trị viên theo dõi; dữ liệu vẫn thuộc người nhập liệu được giao." : "Dữ liệu đã hoàn tất phê duyệt trên tổng số hồ sơ đang theo dõi."}</p>
          </div>
          <Progress type="dashboard" size={122} percent={progress} strokeWidth={9} strokeColor={{ "0%": "#009edb", "100%": "#26b780" }} trailColor="rgba(255,255,255,.18)" />
        </section>

        <section className="overview-statuses">
          <div className="overview-section-heading">
            <strong>Trạng thái hồ sơ</strong>
            <span>{batches.length} đợt đang theo dõi</span>
          </div>
          <div className="overview-status-list">
            {statuses.map((status) => (
              <div className="overview-status-row" key={status.key}>
                <i style={{ color: status.color, background: `${status.color}16` }}>{status.icon}</i>
                <span>{status.label}</span>
                <b>{status.value}</b>
              </div>
            ))}
          </div>
        </section>

        <section className="overview-actions">
          <div className="overview-section-heading"><strong>Đi nhanh</strong></div>
          {canAssign ? <>
            {canApprove && <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => navigate("/approvals")}>Hồ sơ chờ duyệt</Button>}
            <Button icon={<DatabaseOutlined />} onClick={() => navigate("/workspaces")}>Theo dõi và giao việc</Button>
            <small>Quản trị viên theo dõi tiến độ và chuyển giao; không nhập hoặc xóa dữ liệu thay người được giao.</small>
          </> : <>
            <Button type="primary" icon={<EditOutlined />} onClick={() => navigate("/my-work")}>Mở việc của tôi</Button>
            <Button icon={<DatabaseOutlined />} onClick={() => navigate("/workspaces")}>Chọn mẫu hồ sơ</Button>
            <small>Tiếp tục hồ sơ đang nhập hoặc tạo đợt từ một mẫu đã phát hành.</small>
          </>}
        </section>
      </div>

      <section className="overview-recent">
        <div className="overview-recent-heading">
          <div><FolderOpenOutlined /><strong>Đợt hồ sơ gần đây</strong></div>
          <span>{batches.length} đợt</span>
        </div>
        {batches.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có đợt hồ sơ" /> : <div className="overview-recent-list">
          {batches.slice(0, 6).map((batch) => {
            const batchTotal = batch.counters?.total ?? 0;
            const batchApproved = batch.counters?.approved ?? 0;
            const batchProgress = batchTotal ? Math.round((batchApproved / batchTotal) * 100) : 0;
            return <button className="overview-recent-row" key={batch.id} type="button" onClick={() => navigate(`/workspaces/${batch.templateCode}/batches/${batch.id}`)}>
              <span className="overview-batch-icon"><FolderOpenOutlined /></span>
              <span className="overview-batch-name"><strong>{batch.batchName}</strong><small>{batch.templateName}{canAssign && batch.assignedDisplayName ? ` · Phụ trách: ${batch.assignedDisplayName}` : ""}</small></span>
              <span className="overview-batch-count"><b>{batchTotal}</b><small>hồ sơ</small></span>
              <span className="overview-batch-progress"><Progress percent={batchProgress} showInfo={false} size="small" /><small>{batchProgress}% hoàn thành</small></span>
              <ArrowRightOutlined />
            </button>;
          })}
        </div>}
      </section>
    </Card>
  </div>;
}
