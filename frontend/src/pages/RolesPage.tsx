import { EditOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Card, Checkbox, Form, Input, Modal, Space, Switch, Table, Tag } from "antd";
import { useMemo, useState } from "react";
import { securityAdminApi } from "../api/auth";
import { getErrorMessage } from "../api/client";
import { Can } from "../auth/Can";
import { Permissions } from "../auth/permissions";
import { PageError, PageLoading } from "../components/ApiState";
import type { ManagedRole } from "../auth/types";

interface RoleFormValue {
  code: string;
  name: string;
  description?: string;
  active: boolean;
  functionIds: string[];
}

export function RolesPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<RoleFormValue>();
  const [keyword, setKeyword] = useState("");
  const [editing, setEditing] = useState<ManagedRole>();
  const [open, setOpen] = useState(false);
  const rolesQuery = useQuery({ queryKey: ["security-roles"], queryFn: securityAdminApi.roles });
  const functionsQuery = useQuery({ queryKey: ["security-functions"], queryFn: securityAdminApi.functions });
  const saveMutation = useMutation({
    mutationFn: async (value: RoleFormValue) => {
      const saved = editing
        ? await securityAdminApi.updateRole(editing.id, { code: value.code, name: value.name, description: value.description, active: value.active, rowVersion: editing.rowVersion })
        : await securityAdminApi.createRole({ code: value.code, name: value.name, description: value.description, active: value.active, rowVersion: 0 });
      if (!saved.systemRole) await securityAdminApi.assignRoleFunctions(saved.id, value.functionIds);
      return saved;
    },
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["security-roles"] }); setOpen(false); message.success(editing ? "Đã cập nhật vai trò" : "Đã tạo vai trò"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const roles = useMemo(() => {
    const normalized = keyword.trim().toLocaleLowerCase("vi");
    const activeRoles = (rolesQuery.data ?? []).filter((role) => role.active);
    return normalized ? activeRoles.filter((role) => `${role.code} ${role.name}`.toLocaleLowerCase("vi").includes(normalized)) : activeRoles;
  }, [keyword, rolesQuery.data]);
  const groupedFunctions = useMemo(() => Object.entries((functionsQuery.data ?? []).reduce<Record<string, typeof functionsQuery.data>>((groups, item) => {
    const group = item.functionGroup || "OTHER";
    (groups[group] ??= []).push(item);
    return groups;
  }, {})), [functionsQuery.data]);

  const showForm = (role?: ManagedRole) => {
    setEditing(role);
    const functionIds = role?.functions.map((code) => functionsQuery.data?.find((item) => item.code === code)?.id).filter((id): id is string => Boolean(id)) ?? [];
    form.setFieldsValue(role ? { ...role, functionIds } : { code: "", name: "", active: true, functionIds: [] });
    setOpen(true);
  };

  if (rolesQuery.isLoading || functionsQuery.isLoading) return <PageLoading />;
  if (rolesQuery.isError || functionsQuery.isError) return <PageError message={getErrorMessage(rolesQuery.error || functionsQuery.error)} onRetry={() => { void rolesQuery.refetch(); void functionsQuery.refetch(); }} />;

  return (
    <div className="page-stack">
      <div className="toolbar surface-card management-toolbar">
        <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên hoặc mã vai trò..." value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        <Can permission={Permissions.ROLE_MANAGE}><Button type="primary" icon={<PlusOutlined />} onClick={() => showForm()}>Thêm vai trò</Button></Can>
      </div>
      <Card styles={{ body: { padding: 0 } }}>
        <Table rowKey="id" dataSource={roles} pagination={false} scroll={{ x: 800 }} columns={[
          { title: "Vai trò", key: "role", render: (_, role) => <div className="security-main-cell"><strong>{role.name}</strong><small>{role.code}</small></div> },
          { title: "Quyền", dataIndex: "functions", render: (codes: string[]) => <span>{codes?.length ?? 0} chức năng</span> },
          { title: "Trạng thái", dataIndex: "active", width: 150, render: (active: boolean) => <Tag color={active ? "green" : "default"}>{active ? "Đang sử dụng" : "Tạm ngưng"}</Tag> },
          { title: "Thao tác", key: "actions", width: 120, render: (_, role) => <Can permission={Permissions.ROLE_MANAGE}><Button type="text" icon={<EditOutlined />} onClick={() => showForm(role)}>Sửa</Button></Can> },
        ]} />
      </Card>
      <Modal title={editing ? "Cập nhật vai trò" : "Thêm vai trò"} width={760} open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={saveMutation.isPending} okText="Lưu vai trò">
        <Form<RoleFormValue> form={form} layout="vertical" onFinish={(value) => saveMutation.mutate(value)} requiredMark="optional">
          <Space align="start" style={{ width: "100%" }} size={16}>
            <Form.Item name="code" label="Mã vai trò" rules={[{ required: true, message: "Nhập mã vai trò" }, { pattern: /^[A-Z][A-Z0-9_]{2,99}$/, message: "Dùng chữ in hoa, số và gạch dưới" }]}><Input disabled={Boolean(editing)} /></Form.Item>
            <Form.Item name="name" label="Tên vai trò" rules={[{ required: true, message: "Nhập tên vai trò" }]}><Input /></Form.Item>
            <Form.Item name="active" label="Đang sử dụng" valuePropName="checked"><Switch disabled={editing?.systemRole} /></Form.Item>
          </Space>
          <Form.Item name="description" label="Mô tả"><Input.TextArea rows={2} /></Form.Item>
          <Form.Item name="functionIds" label="Các chức năng được phép" rules={[{ required: true, message: "Chọn ít nhất một chức năng" }]}>
            <Checkbox.Group className="permission-checkbox-group" disabled={editing?.systemRole}>
              {groupedFunctions.map(([group, items]) => <div className="permission-group" key={group}><strong>{group}</strong><div>{items?.map((item) => <Checkbox key={item.id} value={item.id}><span>{item.name}</span><small>{item.code}</small></Checkbox>)}</div></div>)}
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
