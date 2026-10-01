import { EyeOutlined, SearchOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, DatePicker, Empty, Input, Select, Space, Table, Tabs } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { Dayjs } from "dayjs";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { securityAdminApi } from "../api/auth";
import { batchApi, workItemApi } from "../api/batches";
import { getErrorMessage } from "../api/client";
import { PageError } from "../components/ApiState";
import { WorkflowStatusTag } from "../components/WorkflowStatusTag";
import type { WorkItem, WorkItemStatus } from "../types";

export function WorkQueuePage({ approval = false }: { approval?: boolean }) {
  const navigate = useNavigate();
  const [status, setStatus] = useState<WorkItemStatus | "ACTIVE">(approval ? "PENDING_APPROVAL" : "ACTIVE");
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [batchId, setBatchId] = useState<string>();
  const [assignedUserId, setAssignedUserId] = useState<string>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const itemsQuery = useQuery({
    queryKey: ["work-queue", approval, status, appliedKeyword, batchId, assignedUserId, range?.[0]?.format("YYYY-MM-DD"), range?.[1]?.format("YYYY-MM-DD")],
    queryFn: () => workItemApi.list({
      status: status === "ACTIVE" ? undefined : status,
      statuses: status === "ACTIVE" ? ["UNPROCESSED", "DRAFT", "REJECTED"] : undefined,
      keyword: appliedKeyword || undefined,
      batchId,
      assignedUserId,
      mine: !approval,
      fromDate: range?.[0]?.format("YYYY-MM-DD"),
      toDate: range?.[1]?.format("YYYY-MM-DD"),
      page: 0,
      size: 500,
    }),
  });
  const batchesQuery = useQuery({ queryKey: ["batches", "queue-filter", approval], queryFn: () => batchApi.list({ page: 0, size: 200 }) });
  const usersQuery = useQuery({ queryKey: ["users", "queue-filter"], queryFn: () => securityAdminApi.users({ page: 0, size: 100 }), enabled: approval });

  const columns: ColumnsType<WorkItem> = [
    { title: "STT", dataIndex: "sequenceNo", width: 72, align: "center" },
    { title: "Tài liệu", key: "document", minWidth: 260, render: (_, item) => <div className="work-item-file"><strong>{item.documentName}</strong><small>{item.relativePath || item.batchCode}</small></div> },
    { title: "Đợt hồ sơ", key: "batch", width: 260, render: (_, item) => <div className="work-item-file"><strong>{item.batchName}</strong><small>{item.templateName} · {item.batchCode}</small></div> },
    { title: "Trạng thái", dataIndex: "status", width: 145, render: (value: WorkItemStatus) => <WorkflowStatusTag status={value} /> },
    { title: "Người nhập", key: "assignee", width: 180, render: (_, item) => item.assignedDisplayName || item.assignedUsername || "—" },
    { title: "Cập nhật", dataIndex: "updatedAt", width: 165, render: (value?: string) => value ? new Date(value).toLocaleString("vi-VN") : "—" },
    { title: "", key: "action", width: 72, fixed: "right", render: (_, item) => <Button type="text" icon={<EyeOutlined />} aria-label="Mở hồ sơ" onClick={() => navigate(`/work-items/${item.id}`)} /> },
  ];
  const tabs = [
    { key: "ACTIVE", label: "Cần xử lý" },
    { key: "UNPROCESSED", label: "Chờ nhập" },
    { key: "DRAFT", label: "Nháp" },
    { key: "REJECTED", label: "Bị trả lại" },
    { key: "PENDING_APPROVAL", label: "Đã gửi duyệt" },
    { key: "APPROVED", label: "Đã duyệt" },
  ];

  if (itemsQuery.isError) return <PageError message={getErrorMessage(itemsQuery.error)} onRetry={() => void itemsQuery.refetch()} />;
  return <div className="page-stack work-queue-page">
    <Card className="batch-work-card" styles={{ body: { padding: 0 } }}>
      {!approval && <Tabs className="workflow-tabs" activeKey={status} items={tabs} onChange={(key) => setStatus(key as typeof status)} />}
      <div className="queue-filter-grid">
        <Input allowClear prefix={<SearchOutlined />} placeholder="Tên file, mã hoặc tên đợt" value={keyword} onChange={(event) => setKeyword(event.target.value)} onPressEnter={() => setAppliedKeyword(keyword.trim())} />
        <Select allowClear showSearch optionFilterProp="label" placeholder="Tất cả đợt hồ sơ" value={batchId} onChange={setBatchId} options={(batchesQuery.data?.items ?? []).map((batch) => ({ value: batch.id, label: `${batch.templateName} · ${batch.batchName} (${batch.batchCode})` }))} />
        {approval && <Select allowClear showSearch optionFilterProp="label" placeholder="Tất cả người nhập" value={assignedUserId} onChange={setAssignedUserId} options={(usersQuery.data?.items ?? []).map((user) => ({ value: user.id, label: user.displayName }))} />}
        <DatePicker.RangePicker value={range} format="DD-MM-YYYY" onChange={(value) => setRange(value as typeof range)} />
        <Button type="primary" icon={<SearchOutlined />} onClick={() => setAppliedKeyword(keyword.trim())}>Tìm</Button>
      </div>
      <Table rowKey="id" loading={itemsQuery.isLoading} columns={columns} dataSource={itemsQuery.data?.items ?? []} scroll={{ x: 1100 }} onRow={(item) => ({ onClick: () => navigate(`/work-items/${item.id}`), style: { cursor: "pointer" } })}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `${total} hồ sơ` }} locale={{ emptyText: <Empty description={approval ? "Không có hồ sơ đang chờ duyệt" : "Không có hồ sơ trong hàng đợi"} /> }} />
    </Card>
  </div>;
}
