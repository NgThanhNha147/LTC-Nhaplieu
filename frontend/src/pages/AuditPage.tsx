import { ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, DatePicker, Input, Select, Space, Table, Tag } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useState } from "react";
import { auditApi } from "../api/auth";
import { getErrorMessage } from "../api/client";
import { PageError } from "../components/ApiState";

interface AuditFilter { actor: string; action: string; eventType?: string; range: [Dayjs | null, Dayjs | null] | null }
const initialFilter: AuditFilter = { actor: "", action: "", range: null };

export function AuditPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [draft, setDraft] = useState(initialFilter);
  const [filter, setFilter] = useState(initialFilter);
  const query = useQuery({
    queryKey: ["audit-events", filter, page, size],
    queryFn: () => auditApi.events({ actor: filter.actor || undefined, action: filter.action || undefined, eventType: filter.eventType, from: filter.range?.[0]?.startOf("day").toISOString(), to: filter.range?.[1]?.endOf("day").toISOString(), page, size }),
  });

  if (query.isError) return <PageError message={getErrorMessage(query.error)} onRetry={() => void query.refetch()} />;
  const apply = () => { setPage(0); setFilter({ ...draft }); };

  return <div className="page-stack">
    <Card className="audit-filter-card"><div className="audit-filter-grid">
      <Input allowClear prefix={<SearchOutlined />} placeholder="Tên đăng nhập..." value={draft.actor} onChange={(event) => setDraft((current) => ({ ...current, actor: event.target.value }))} onPressEnter={apply} />
      <Input allowClear placeholder="Hành động..." value={draft.action} onChange={(event) => setDraft((current) => ({ ...current, action: event.target.value }))} onPressEnter={apply} />
      <Select allowClear placeholder="Nhóm sự kiện" value={draft.eventType} onChange={(eventType) => setDraft((current) => ({ ...current, eventType }))} options={["AUTHENTICATION", "AUTHORIZATION", "RECORD", "TEMPLATE", "DOCUMENT", "EXPORT", "SECURITY", "BACKUP"].map((value) => ({ value, label: value }))} />
      <DatePicker.RangePicker value={draft.range} format="DD-MM-YYYY" onChange={(range) => setDraft((current) => ({ ...current, range: range as AuditFilter["range"] }))} />
      <Space><Button type="primary" icon={<SearchOutlined />} onClick={apply}>Tra cứu</Button><Button icon={<ReloadOutlined />} onClick={() => { setDraft(initialFilter); setFilter(initialFilter); setPage(0); }}>Đặt lại</Button></Space>
    </div></Card>
    <Card styles={{ body: { padding: 0 } }}><Table rowKey="id" loading={query.isLoading} dataSource={query.data?.items ?? []} scroll={{ x: 1050 }} pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, showSizeChanger: true, onChange: (next, nextSize) => { setPage(next - 1); setSize(nextSize); } }} columns={[
      { title: "Thời gian", dataIndex: "createdAt", width: 175, render: (value: string) => dayjs(value).format("DD-MM-YYYY HH:mm:ss") },
      { title: "Người thực hiện", dataIndex: "actorUsername", width: 170, render: (value?: string) => value || "Hệ thống" },
      { title: "Sự kiện", key: "event", render: (_, event) => <div className="security-main-cell"><strong>{event.action}</strong><small>{event.eventType}</small></div> },
      { title: "Đối tượng", key: "object", render: (_, event) => event.objectType ? `${event.objectType}${event.objectId ? ` · ${event.objectId}` : ""}` : "—" },
      { title: "Kết quả", dataIndex: "result", width: 120, render: (value: string) => <Tag color={value === "SUCCESS" ? "green" : value === "DENIED" ? "orange" : "red"}>{value}</Tag> },
      { title: "IP", dataIndex: "ipAddress", width: 140, render: (value?: string) => value || "—" },
      { title: "Request ID", dataIndex: "requestId", width: 210, ellipsis: true },
    ]} /></Card>
  </div>;
}
