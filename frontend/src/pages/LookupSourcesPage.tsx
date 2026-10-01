import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  App,
  Button,
  Card,
  Col,
  Collapse,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Space,
  Table,
  Tag,
} from "antd";
import { useMemo, useState } from "react";
import { getErrorMessage } from "../api/client";
import { lookupApi } from "../api/lookups";
import { PageError, PageLoading } from "../components/ApiState";
import type { LookupSource, LookupSourcePayload } from "../types";
import { Can } from "../auth/Can";
import { Permissions } from "../auth/permissions";

const emptyLookup: LookupSourcePayload = {
  code: "",
  name: "",
  sourceType: "TABLE",
  sourceSchema: "ref_data",
  sourceTable: "",
  valueColumn: "code",
  labelColumn: "name",
  activeColumn: "active",
  parentColumn: undefined,
  sortColumn: "display_order",
  status: "ACTIVE",
};

function toLookupCode(label: string): string {
  return label.normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[đĐ]/g, "d")
    .replace(/[^A-Za-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toUpperCase()
    .slice(0, 100);
}

export function LookupSourcesPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<LookupSourcePayload>();
  const [editing, setEditing] = useState<LookupSource>();
  const [formOpen, setFormOpen] = useState(false);
  const [preview, setPreview] = useState<LookupSource>();
  const [previewQuery, setPreviewQuery] = useState("");
  const [search, setSearch] = useState("");

  const sourcesQuery = useQuery({ queryKey: ["lookup-sources"], queryFn: lookupApi.list });
  const optionsQuery = useQuery({
    queryKey: ["lookup-options-preview", preview?.code, previewQuery],
    queryFn: () => lookupApi.options(preview!.code, previewQuery),
    enabled: Boolean(preview),
  });

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ["lookup-sources"] });
  const saveMutation = useMutation({
    mutationFn: (value: LookupSourcePayload) => {
      const payload = { ...value, code: value.code || toLookupCode(value.name) };
      return editing ? lookupApi.update(editing.id, payload) : lookupApi.create(payload);
    },
    onSuccess: () => {
      refresh();
      setFormOpen(false);
      message.success(editing ? "Đã cập nhật nguồn danh mục" : "Đã đăng ký nguồn danh mục");
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const removeMutation = useMutation({
    mutationFn: lookupApi.remove,
    onSuccess: () => { refresh(); message.success("Đã xóa nguồn danh mục"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const openForm = (source?: LookupSource) => {
    setEditing(source);
    form.setFieldsValue(source ? { ...source } : emptyLookup);
    setFormOpen(true);
  };

  const sources = sourcesQuery.data ?? [];
  const filteredSources = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase("vi");
    if (!keyword) return sources;
    return sources.filter((source) =>
      `${source.code} ${source.name} ${source.sourceSchema}.${source.sourceTable}`.toLocaleLowerCase("vi").includes(keyword),
    );
  }, [search, sources]);

  if (sourcesQuery.isLoading) return <PageLoading />;
  if (sourcesQuery.isError) return <PageError message={getErrorMessage(sourcesQuery.error)} onRetry={() => void sourcesQuery.refetch()} />;

  return (
    <div className="page-stack lookup-page">
      <div className="toolbar surface-card management-toolbar">
        <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên, mã hoặc bảng nguồn..." value={search} onChange={(event) => setSearch(event.target.value)} />
        <Can permission={Permissions.LOOKUP_MANAGE}><Button type="primary" icon={<PlusOutlined />} onClick={() => openForm()}>Thêm danh mục</Button></Can>
      </div>

      <Card className="lookup-table-card" styles={{ body: { padding: 0 } }}>
        <Table
          rowKey="id"
          dataSource={filteredSources}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: <Empty description="Chưa có nguồn danh mục" /> }}
          columns={[
            {
              title: "TÊN DANH MỤC",
              key: "lookup",
              render: (_, source) => <div className="lookup-name"><strong>{source.name}</strong><small>Dữ liệu lấy từ bảng đã kết nối</small></div>,
            },
            {
              title: "TRẠNG THÁI",
              dataIndex: "status",
              width: 130,
              render: (status: string) => <Tag color={status === "ACTIVE" ? "green" : "default"}>{status === "ACTIVE" ? "Đang sử dụng" : "Tạm ngưng"}</Tag>,
            },
            {
              title: "THAO TÁC",
              key: "actions",
              width: 250,
              render: (_, source) => <Space size={2}>
                <Button type="text" icon={<EyeOutlined />} onClick={() => { setPreviewQuery(""); setPreview(source); }}>Xem giá trị</Button>
                <Can permission={Permissions.LOOKUP_MANAGE}><Button type="text" icon={<EditOutlined />} onClick={() => openForm(source)}>Sửa</Button></Can>
                <Can permission={Permissions.LOOKUP_MANAGE}><Popconfirm title="Xóa danh mục này?" description="Không thể xóa nếu danh mục đang được một trường dữ liệu sử dụng." onConfirm={() => removeMutation.mutate(source.id)}>
                  <Button danger type="text" icon={<DeleteOutlined />} />
                </Popconfirm></Can>
              </Space>,
            },
          ]}
        />
      </Card>

      <Modal
        title={editing ? "Sửa danh mục lựa chọn" : "Thêm danh mục lựa chọn"}
        open={formOpen}
        width={720}
        onCancel={() => setFormOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="Lưu danh mục"
      >
        <Form form={form} layout="vertical" onFinish={(value) => saveMutation.mutate(value)}>
          <Form.Item name="code" hidden><Input /></Form.Item>
          <Form.Item name="sourceSchema" hidden><Input /></Form.Item>
          <Form.Item name="sourceType" hidden><Input /></Form.Item>
          <Form.Item name="status" hidden><Input /></Form.Item>
          <Form.Item name="name" label="Tên danh mục" rules={[{ required: true, message: "Nhập tên danh mục" }]}>
            <Input placeholder="Ví dụ: Loại giấy tờ tùy thân" onBlur={(event) => { if (!form.getFieldValue("code")) form.setFieldValue("code", toLookupCode(event.target.value)); }} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={24}><Form.Item name="sourceTable" label="Bảng chứa dữ liệu danh mục" rules={[{ required: true, message: "Nhập tên bảng chứa danh mục" }, { pattern: /^[a-z][a-z0-9_]{0,62}$/, message: "Tên bảng dùng chữ thường, số và dấu gạch dưới" }]} extra="Ví dụ: document_type"><Input placeholder="document_type" /></Form.Item></Col>
          </Row>
          <Row gutter={12}>
            <Col xs={24} md={12}><Form.Item name="valueColumn" label="Cột chứa mã" rules={[{ required: true, message: "Nhập tên cột chứa mã" }]}><Input placeholder="code" /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item name="labelColumn" label="Cột chứa tên hiển thị" rules={[{ required: true, message: "Nhập tên cột hiển thị" }]}><Input placeholder="name" /></Form.Item></Col>
          </Row>
          <Collapse ghost size="small" items={[{ key: "lookup-options", label: "Tùy chọn nâng cao", children: <Row gutter={12}>
            <Col xs={24} md={8}><Form.Item name="sortColumn" label="Cột sắp xếp"><Input /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="activeColumn" label="Cột trạng thái"><Input /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="parentColumn" label="Cột danh mục cha"><Input /></Form.Item></Col>
          </Row> }]} />
        </Form>
      </Modal>

      <Drawer title={preview?.name} width={480} open={Boolean(preview)} onClose={() => setPreview(undefined)}>
        {preview && <>
          <Input.Search
            allowClear
            enterButton={<ReloadOutlined />}
            placeholder="Tìm thử theo tên hiển thị..."
            onSearch={setPreviewQuery}
          />
          <div className="lookup-option-list">
            {optionsQuery.isLoading ? <PageLoading /> : (optionsQuery.data ?? []).map((option) => (
              <div key={String(option.value)}><code>{option.value}</code><span>{option.label}</span></div>
            ))}
            {!optionsQuery.isLoading && !optionsQuery.data?.length && <Empty description="Không có giá trị phù hợp" />}
          </div>
        </>}
      </Drawer>
    </div>
  );
}
