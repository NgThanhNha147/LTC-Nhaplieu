import {
  CalendarOutlined, DeleteOutlined, EditOutlined, ExportOutlined, EyeOutlined,
  FilterOutlined, MoreOutlined, PaperClipOutlined, PlusOutlined, SearchOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  App, Button, Card, DatePicker, Dropdown, Empty, Input, Modal,
  Popover, Segmented, Select, Space, Table, Tag,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import { recordApi } from "../api/records";
import { templateApi } from "../api/templates";
import { PageError, PageLoading } from "../components/ApiState";
import type { DynamicRecord, FieldDefinition, SearchFilter, SearchRequest } from "../types";

type PeriodKey = "ALL" | "TODAY" | "LAST_7_DAYS" | "THIS_MONTH" | "LAST_MONTH" | "CUSTOM";
type AttachmentFilter = "ALL" | "WITH" | "WITHOUT";

interface FilterDraft {
  keyword: string;
  period: PeriodKey;
  dateField: "createdAt" | "updatedAt";
  range: [Dayjs | null, Dayjs | null] | null;
  status?: string;
  attachment: AttachmentFilter;
  field?: string;
  operator: string;
  value: string;
}

const initialDraft: FilterDraft = {
  keyword: "", period: "ALL", dateField: "createdAt", range: null,
  attachment: "ALL", operator: "CONTAINS", value: "",
};

const operatorLabels: Record<string, string> = {
  CONTAINS: "Có chứa", EQ: "Bằng", STARTS_WITH: "Bắt đầu bằng",
  GT: "Lớn hơn", GTE: "Lớn hơn hoặc bằng", LT: "Nhỏ hơn", LTE: "Nhỏ hơn hoặc bằng",
};

function valueText(value: unknown, field?: FieldDefinition): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "Có" : "Không";
  if (field?.dataType === "DATE" && typeof value === "string") return new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN");
  if (field?.dataType === "DATETIME" && typeof value === "string") return new Date(value).toLocaleString("vi-VN");
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function recordStatus(status?: string) {
  return status === "COMPLETED" ? <Tag color="green">Đã hoàn tất</Tag> : <Tag color="gold">Đang nhập</Tag>;
}

function periodRange(period: PeriodKey, custom: FilterDraft["range"]): [string | undefined, string | undefined] {
  const now = dayjs();
  if (period === "ALL") return [undefined, undefined];
  if (period === "TODAY") return [now.format("YYYY-MM-DD"), now.format("YYYY-MM-DD")];
  if (period === "LAST_7_DAYS") return [now.subtract(6, "day").format("YYYY-MM-DD"), now.format("YYYY-MM-DD")];
  if (period === "THIS_MONTH") return [now.startOf("month").format("YYYY-MM-DD"), now.endOf("month").format("YYYY-MM-DD")];
  if (period === "LAST_MONTH") {
    const lastMonth = now.subtract(1, "month");
    return [lastMonth.startOf("month").format("YYYY-MM-DD"), lastMonth.endOf("month").format("YYYY-MM-DD")];
  }
  return [custom?.[0]?.format("YYYY-MM-DD"), custom?.[1]?.format("YYYY-MM-DD")];
}

function toSearchRequest(draft: FilterDraft, page: number, size: number): SearchRequest {
  const [fromDate, toDate] = periodRange(draft.period, draft.range);
  const filters: SearchFilter[] = draft.field && draft.value !== ""
    ? [{ field: draft.field, operator: draft.operator, value: draft.value }]
    : [];
  return {
    keyword: draft.keyword.trim() || undefined,
    dateField: draft.dateField,
    fromDate,
    toDate,
    status: draft.status,
    hasDocument: draft.attachment === "ALL" ? undefined : draft.attachment === "WITH",
    filters,
    sort: [{ field: "createdAt", direction: "DESC" }],
    page,
    size,
  };
}

export function RecordsPage() {
  const { templateCode = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [draft, setDraft] = useState<FilterDraft>(initialDraft);
  const [applied, setApplied] = useState<FilterDraft>(initialDraft);
  const [exportRangeOpen, setExportRangeOpen] = useState(false);
  const [exportRange, setExportRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [timeOpen, setTimeOpen] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);

  const formQuery = useQuery({ queryKey: ["form", templateCode], queryFn: () => templateApi.form(templateCode), enabled: Boolean(templateCode) });
  const request = useMemo(() => toSearchRequest(applied, page, size), [applied, page, size]);
  const recordsQuery = useQuery({ queryKey: ["records", templateCode, request], queryFn: () => recordApi.search(templateCode, request), enabled: formQuery.isSuccess });
  const deleteMutation = useMutation({
    mutationFn: (record: DynamicRecord) => recordApi.remove(templateCode, record.id, record.rowVersion ?? 0),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["records", templateCode] }); message.success("Đã xóa bản ghi"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const exportMutation = useMutation({
    mutationFn: (exportRequest: SearchRequest) => recordApi.export(templateCode, { ...exportRequest, page: 0, size: 200 }),
    onSuccess: () => message.success("Đã tải file export"),
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const allFields = useMemo(() => formQuery.data?.groups.flatMap((group) => group.fields ?? []) ?? [], [formQuery.data]);
  const searchable = allFields.filter((field) => field.searchable);
  const displayFields = allFields.filter((field) => !field.hidden).slice(0, 4);
  const selectedField = searchable.find((field) => field.fieldCode === draft.field);
  const hasAppliedFilters = Boolean(
    applied.keyword || applied.period !== "ALL" || applied.status
    || applied.attachment !== "ALL" || applied.field,
  );
  const operators = selectedField && ["INTEGER", "DECIMAL", "DATE", "DATETIME"].includes(selectedField.dataType)
    ? ["EQ", "GT", "GTE", "LT", "LTE"]
    : selectedField?.dataType === "BOOLEAN" ? ["EQ"] : ["CONTAINS", "EQ", "STARTS_WITH"];

  const columns: ColumnsType<DynamicRecord> = [
    ...displayFields.map((field: FieldDefinition) => ({
      title: field.label, key: field.fieldCode, ellipsis: true,
      render: (_: unknown, record: DynamicRecord) => valueText(record.data?.[field.fieldCode], field),
    })),
    { title: "Ngày nhập", dataIndex: "createdAt", width: 160, render: (value?: string) => value ? new Date(value).toLocaleString("vi-VN") : "—" },
    { title: "Tài liệu", key: "document", width: 105, align: "center", render: (_: unknown, record) => record.sourceDocumentId ? <Tag icon={<PaperClipOutlined />} color="blue">Có file</Tag> : <span className="muted-cell">Không</span> },
    { title: "Trạng thái", key: "status", width: 130, render: (_: unknown, record) => recordStatus(record.recordStatus) },
    {
      title: "Thao tác", key: "actions", fixed: "right", width: 150,
      render: (_: unknown, record) => <Space size={4} onClick={(event) => event.stopPropagation()}>
        <Button className="compare-action" icon={<EyeOutlined />} onClick={() => navigate(`/workspaces/${templateCode}/records/${record.id}?mode=compare`)}>Mở</Button>
        <Dropdown trigger={["click"]} menu={{ items: [
          { key: "edit", icon: <EditOutlined />, label: "Chỉnh sửa", onClick: () => navigate(`/workspaces/${templateCode}/records/${record.id}`) },
          { key: "delete", danger: true, icon: <DeleteOutlined />, label: "Xóa hồ sơ", onClick: () => modal.confirm({ title: "Xóa hồ sơ này?", content: "Dữ liệu đã xóa không thể khôi phục.", okText: "Xóa", okButtonProps: { danger: true }, onOk: () => deleteMutation.mutateAsync(record) }) },
        ] }}><Button type="text" icon={<MoreOutlined />} aria-label="Thêm thao tác" /></Dropdown>
      </Space>,
    },
  ];

  const applySearch = (next = draft) => { setPage(0); setApplied({ ...next }); };
  const applyPeriod = (period: PeriodKey) => {
    const next = { ...draft, period };
    setDraft(next);
    if (period !== "CUSTOM") {
      applySearch(next);
      setTimeOpen(false);
    }
  };
  const resetFilters = () => { setDraft(initialDraft); setApplied(initialDraft); setPage(0); };
  const changeFilterField = (field?: string) => {
    const definition = searchable.find((item) => item.fieldCode === field);
    setDraft((current) => ({
      ...current, field, value: "",
      operator: definition && ["INTEGER", "DECIMAL", "DATE", "DATETIME", "BOOLEAN"].includes(definition.dataType) ? "EQ" : "CONTAINS",
    }));
  };
  const exportCurrent = () => exportMutation.mutate(toSearchRequest(applied, 0, 200));
  const exportThisMonth = () => exportMutation.mutate(toSearchRequest({ ...applied, period: "THIS_MONTH", range: null }, 0, 200));
  const exportCustom = () => {
    if (!exportRange?.[0] || !exportRange[1]) return message.warning("Chọn đủ khoảng ngày cần export");
    exportMutation.mutate(toSearchRequest({ ...applied, period: "CUSTOM", range: exportRange }, 0, 200));
    setExportRangeOpen(false);
  };

  const applyCustomPeriod = () => {
    if (draft.period === "CUSTOM" && (!draft.range?.[0] || !draft.range[1])) {
      message.warning("Chọn đủ khoảng ngày cần tìm");
      return;
    }
    applySearch();
    setTimeOpen(false);
  };

  const timePopover = (
    <div className="search-popover-content time-popover-content">
      <Segmented value={draft.period} onChange={(value) => applyPeriod(value as PeriodKey)} options={[
        { value: "ALL", label: "Tất cả" }, { value: "TODAY", label: "Hôm nay" }, { value: "LAST_7_DAYS", label: "7 ngày" },
        { value: "THIS_MONTH", label: "Tháng này" }, { value: "LAST_MONTH", label: "Tháng trước" }, { value: "CUSTOM", label: "Tùy chọn" },
      ]} />
      {draft.period === "CUSTOM" && <DatePicker.RangePicker className="popover-date-range" value={draft.range} format="DD-MM-YYYY" onChange={(range) => setDraft((current) => ({ ...current, range: range as FilterDraft["range"] }))} />}
      <div className="popover-footer"><small>Đang áp dụng: {periodRange(applied.period, applied.range).filter(Boolean).join(" → ") || "Tất cả thời gian"}</small><Button type="primary" size="small" onClick={applyCustomPeriod}>Áp dụng</Button></div>
    </div>
  );

  const filterPopover = (
    <div className="search-popover-content filter-popover-content">
      <div className="advanced-filter-grid">
        <label><span>Căn cứ ngày</span><Select value={draft.dateField} onChange={(dateField) => setDraft((current) => ({ ...current, dateField }))} options={[{ value: "createdAt", label: "Ngày nhập" }, { value: "updatedAt", label: "Ngày cập nhật" }]} /></label>
        <label><span>Trạng thái hồ sơ</span><Select allowClear placeholder="Tất cả trạng thái" value={draft.status} onChange={(status) => setDraft((current) => ({ ...current, status }))} options={[{ value: "DRAFT", label: "Đang nhập" }, { value: "COMPLETED", label: "Đã hoàn tất" }]} /></label>
        <label><span>Tài liệu đính kèm</span><Select value={draft.attachment} onChange={(attachment) => setDraft((current) => ({ ...current, attachment }))} options={[{ value: "ALL", label: "Tất cả" }, { value: "WITH", label: "Có tài liệu" }, { value: "WITHOUT", label: "Chưa có tài liệu" }]} /></label>
        <label><span>Trường nghiệp vụ</span><Select allowClear showSearch placeholder="Chọn trường" value={draft.field} onChange={changeFilterField} options={searchable.map((field) => ({ value: field.fieldCode, label: field.label }))} /></label>
        <label><span>Điều kiện</span><Select value={draft.operator} onChange={(operator) => setDraft((current) => ({ ...current, operator }))} options={operators.map((value) => ({ value, label: operatorLabels[value] ?? value }))} /></label>
        <label><span>Giá trị</span>{selectedField?.dataType === "DATE" ? (
          <DatePicker value={draft.value ? dayjs(draft.value) : null} format="DD-MM-YYYY" placeholder="DD-MM-YYYY" onChange={(date) => setDraft((current) => ({ ...current, value: date?.format("YYYY-MM-DD") ?? "" }))} />
        ) : selectedField?.dataType === "DATETIME" ? (
          <DatePicker showTime value={draft.value ? dayjs(draft.value) : null} format="DD-MM-YYYY HH:mm" placeholder="DD-MM-YYYY HH:mm" onChange={(date) => setDraft((current) => ({ ...current, value: date?.toISOString() ?? "" }))} />
        ) : selectedField?.dataType === "BOOLEAN" ? (
          <Select allowClear placeholder="Chọn giá trị" value={draft.value || undefined} onChange={(value) => setDraft((current) => ({ ...current, value: value ?? "" }))} options={[{ value: "true", label: "Có" }, { value: "false", label: "Không" }]} />
        ) : (
          <Input allowClear placeholder="Nhập giá trị cần lọc" value={draft.value} onChange={(event) => setDraft((current) => ({ ...current, value: event.target.value }))} onPressEnter={() => { applySearch(); setFilterOpen(false); }} />
        )}</label>
      </div>
      <div className="popover-footer"><Button onClick={resetFilters}>Đặt lại</Button><Button type="primary" icon={<FilterOutlined />} onClick={() => { applySearch(); setFilterOpen(false); }}>Áp dụng bộ lọc</Button></div>
    </div>
  );

  if (formQuery.isLoading) return <PageLoading />;
  if (formQuery.isError) return <PageError message={getErrorMessage(formQuery.error)} onRetry={() => void formQuery.refetch()} />;
  return (
    <div className="page-stack">
      <Card className="search-command-card">
        <div className="compact-search-toolbar">
          <Input size="large" allowClear prefix={<SearchOutlined />} placeholder="Tìm hồ sơ..." value={draft.keyword} onChange={(event) => setDraft((current) => ({ ...current, keyword: event.target.value }))} onPressEnter={() => applySearch()} />
          <Button className="toolbar-icon-button toolbar-search-button" size="large" type="primary" icon={<SearchOutlined />} aria-label="Tìm kiếm" title="Tìm kiếm" onClick={() => applySearch()} />
          <Popover trigger="click" placement="bottomRight" open={timeOpen} onOpenChange={setTimeOpen} title="Khoảng thời gian" content={timePopover}>
            <Button className={`toolbar-icon-button ${applied.period !== "ALL" ? "toolbar-button-active" : ""}`} size="large" icon={<CalendarOutlined />} aria-label="Thời gian" title="Thời gian" />
          </Popover>
          <Popover trigger="click" placement="bottomRight" open={filterOpen} onOpenChange={setFilterOpen} title="Bộ lọc nâng cao" content={filterPopover}>
            <Button className={`toolbar-icon-button ${applied.status || applied.attachment !== "ALL" || applied.field ? "toolbar-button-active" : ""}`} size="large" icon={<FilterOutlined />} aria-label="Bộ lọc" title="Bộ lọc" />
          </Popover>
          <Dropdown menu={{ items: [
            { key: "current", label: "Theo bộ lọc hiện tại", onClick: exportCurrent },
            { key: "month", label: "Dữ liệu tháng này", onClick: exportThisMonth },
            { type: "divider" },
            { key: "custom", label: "Chọn khoảng ngày...", onClick: () => setExportRangeOpen(true) },
          ] }}><Button className="toolbar-action-button" size="large" icon={<ExportOutlined />} loading={exportMutation.isPending}>Xuất Excel</Button></Dropdown>
          <Button className="toolbar-action-button" size="large" type="primary" icon={<PlusOutlined />} onClick={() => navigate(`/workspaces/${templateCode}/new`)}>Tạo hồ sơ mới</Button>
        </div>
        {hasAppliedFilters && <div className="search-active-summary"><strong>Bộ lọc đang dùng</strong>{applied.period !== "ALL" && <Tag>{periodRange(applied.period, applied.range).filter(Boolean).join(" → ")}</Tag>}{applied.keyword && <Tag color="blue">{applied.keyword}</Tag>}{applied.status && <Tag color="gold">{applied.status === "DRAFT" ? "Đang nhập" : "Đã hoàn tất"}</Tag>}{applied.attachment !== "ALL" && <Tag color="cyan">{applied.attachment === "WITH" ? "Có tài liệu" : "Chưa có tài liệu"}</Tag>}{applied.field && <Tag color="geekblue">Lọc theo trường dữ liệu</Tag>}<Button size="small" type="link" onClick={resetFilters}>Xóa bộ lọc</Button></div>}
      </Card>

      <Card className="record-table-card" styles={{ body: { padding: 0 } }}>
        <Table rowKey="id" loading={recordsQuery.isLoading} columns={columns} dataSource={recordsQuery.data?.items ?? []} scroll={{ x: 1200 }}
          onRow={(record) => ({ onClick: () => navigate(`/workspaces/${templateCode}/records/${record.id}?mode=compare`), style: { cursor: "pointer" } })}
          locale={{ emptyText: <Empty description="Chưa có hồ sơ phù hợp"><Button type="primary" onClick={() => navigate(`/workspaces/${templateCode}/new`)}>Tạo hồ sơ đầu tiên</Button></Empty> }}
          pagination={{ current: page + 1, pageSize: size, total: recordsQuery.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `${total} bản ghi`, onChange: (nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); } }} />
      </Card>

      <Modal title="Export theo khoảng ngày" open={exportRangeOpen} onCancel={() => setExportRangeOpen(false)} onOk={exportCustom} okText="Xuất Excel" confirmLoading={exportMutation.isPending}>
        <p>Khoảng ngày áp dụng theo <strong>{applied.dateField === "createdAt" ? "Ngày nhập" : "Ngày cập nhật"}</strong>.</p>
        <DatePicker.RangePicker value={exportRange} onChange={(range) => setExportRange(range as typeof exportRange)} format="DD-MM-YYYY" style={{ width: "100%" }} />
      </Modal>
    </div>
  );
}
