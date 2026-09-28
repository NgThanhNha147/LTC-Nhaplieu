import { DeleteOutlined, EditOutlined, FormOutlined, MoreOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Card, Col, Collapse, Dropdown, Empty, Form, Input, Modal, Row } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { templateApi } from "../api/templates";
import { getErrorMessage } from "../api/client";
import { PageError, PageLoading } from "../components/ApiState";
import { StatusTag } from "../components/StatusTag";
import type { TemplateSummary } from "../types";

interface TemplateFormValue {
  code: string;
  name: string;
  description?: string;
}

function toTemplateCode(value: string): string {
  const normalized = value.normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[đĐ]/g, "d")
    .replace(/[^A-Za-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toUpperCase();
  return (normalized || "BIEU_MAU_MOI").slice(0, 100);
}

export function TemplatesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<TemplateFormValue>();
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<TemplateSummary | null>(null);
  const [open, setOpen] = useState(false);
  const query = useQuery({ queryKey: ["templates"], queryFn: () => templateApi.list() });

  const saveMutation = useMutation({
    mutationFn: (values: TemplateFormValue) => {
      const payload = { ...values, code: values.code || toTemplateCode(values.name) };
      return editing ? templateApi.update(editing.id, payload) : templateApi.create(payload);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["templates"] });
      message.success(editing ? "Đã cập nhật biểu mẫu" : "Đã tạo biểu mẫu");
      setOpen(false);
      setEditing(null);
      form.resetFields();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const deleteMutation = useMutation({
    mutationFn: templateApi.remove,
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["templates"] }); message.success("Đã xóa biểu mẫu"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const templates = (query.data?.items ?? []).filter((item) => !item.code.startsWith("SMOKE_"));
  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return keyword ? templates.filter((item) => `${item.code} ${item.name} ${item.description ?? ""}`.toLowerCase().includes(keyword)) : templates;
  }, [search, templates]);

  const showCreate = () => { setEditing(null); form.resetFields(); setOpen(true); };
  const showEdit = (item: TemplateSummary) => { setEditing(item); form.setFieldsValue(item); setOpen(true); };

  if (query.isLoading) return <PageLoading />;
  if (query.isError) return <PageError message={getErrorMessage(query.error)} onRetry={() => void query.refetch()} />;

  return (
    <div className="page-stack">
      <div className="toolbar surface-card management-toolbar">
        <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên hoặc mã biểu mẫu..." value={search} onChange={(event) => setSearch(event.target.value)} />
        <Button type="primary" icon={<PlusOutlined />} onClick={showCreate}>Tạo biểu mẫu</Button>
      </div>

      {filtered.length === 0 ? (
        <div className="surface-card"><Empty description="Chưa có biểu mẫu phù hợp"><Button type="primary" onClick={showCreate}>Tạo biểu mẫu đầu tiên</Button></Empty></div>
      ) : (
        <Row gutter={[18, 18]}>
          {filtered.map((item) => (
            <Col xs={24} md={12} xl={8} key={item.id}>
              <Card className="template-card">
                <div className="template-card-top">
                  <StatusTag status={item.status} />
                  <Dropdown trigger={["click"]} menu={{ items: [
                    { key: "data", icon: <FormOutlined />, label: "Xem hồ sơ", onClick: () => navigate(`/workspaces/${item.code}`) },
                    { key: "edit", icon: <EditOutlined />, label: "Sửa tên và mô tả", onClick: () => showEdit(item) },
                    { type: "divider" },
                    { key: "delete", danger: true, icon: <DeleteOutlined />, label: "Xóa biểu mẫu", onClick: () => modal.confirm({ title: "Xóa biểu mẫu này?", content: "Chỉ có thể xóa biểu mẫu chưa phát sinh dữ liệu.", okText: "Xóa", okButtonProps: { danger: true }, onOk: () => deleteMutation.mutateAsync(item.id) }) },
                  ] }}>
                    <Button type="text" icon={<MoreOutlined />} aria-label="Thêm thao tác" />
                  </Dropdown>
                </div>
                <h2>{item.name}</h2>
                <p>{item.description || "Chưa có mô tả cho biểu mẫu này."}</p>
                <Button block type="primary" icon={<EditOutlined />} onClick={() => navigate(`/templates/${item.id}/designer`)}>Cấu hình biểu mẫu</Button>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Modal title={editing ? "Cập nhật biểu mẫu" : "Tạo biểu mẫu mới"} open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={saveMutation.isPending} okText={editing ? "Lưu thay đổi" : "Tạo và cấu hình bảng"}>
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)} requiredMark="optional">
          <Form.Item name="name" label="Tên biểu mẫu" rules={[{ required: true, message: "Nhập tên biểu mẫu" }]}>
            <Input
              placeholder="Ví dụ: Hồ sơ khách hàng"
              onBlur={(event) => {
                if (!editing && !form.getFieldValue("code")) form.setFieldValue("code", toTemplateCode(event.target.value));
              }}
            />
          </Form.Item>
          <Form.Item name="description" label="Mô tả"><Input.TextArea rows={3} placeholder="Mục đích và phạm vi sử dụng..." /></Form.Item>
          <Collapse
            ghost
            size="small"
            items={[{
              key: "advanced-template",
              label: "Tùy chọn nâng cao",
              children: (
                <Form.Item
                  name="code"
                  label="Mã biểu mẫu"
                  rules={[
                    { pattern: /^[A-Z][A-Z0-9_]{1,99}$/, message: "Dùng chữ in hoa, số và dấu gạch dưới" },
                  ]}
                  extra="Hệ thống tự tạo từ tên biểu mẫu. Chỉ cần thay đổi khi có quy ước mã riêng."
                >
                  <Input disabled={Boolean(editing)} placeholder="HO_SO_KHACH_HANG" />
                </Form.Item>
              ),
            }]}
          />
        </Form>
      </Modal>
    </div>
  );
}
