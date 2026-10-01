import { EditOutlined, PlusOutlined, SearchOutlined, UnlockOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Card, Form, Input, Modal, Select, Space, Switch, Table, Tag } from "antd";
import { useMemo, useState } from "react";
import { securityAdminApi } from "../api/auth";
import { getErrorMessage } from "../api/client";
import { Can } from "../auth/Can";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";
import { PageError, PageLoading } from "../components/ApiState";
import type { ManagedUser } from "../auth/types";

interface UserFormValue {
  username: string;
  displayName: string;
  email?: string;
  password?: string;
  roleIds: string[];
  status: string;
  mustChangePassword: boolean;
}

export function UsersPage() {
  const { message } = App.useApp();
  const { user: currentUser } = useAuth();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<UserFormValue>();
  const [keyword, setKeyword] = useState("");
  const [editing, setEditing] = useState<ManagedUser>();
  const [open, setOpen] = useState(false);
  const [resetUser, setResetUser] = useState<ManagedUser>();
  const [resetForm] = Form.useForm<{ password: string }>();
  const usersQuery = useQuery({ queryKey: ["security-users"], queryFn: () => securityAdminApi.users({ size: 500 }) });
  const rolesQuery = useQuery({ queryKey: ["security-roles"], queryFn: securityAdminApi.roles });

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ["security-users"] });
  const saveMutation = useMutation({
    mutationFn: async (value: UserFormValue) => {
      if (editing) {
        const updated = await securityAdminApi.updateUser(editing.id, {
          displayName: value.displayName,
          email: value.email,
          status: value.status,
          mustChangePassword: value.mustChangePassword,
          rowVersion: editing.rowVersion,
        });
        if (editing.id !== currentUser?.id) await securityAdminApi.assignUserRoles(editing.id, value.roleIds);
        return updated;
      }
      return securityAdminApi.createUser({
        username: value.username,
        password: value.password!,
        displayName: value.displayName,
        email: value.email,
        roleIds: value.roleIds,
        mustChangePassword: value.mustChangePassword,
      });
    },
    onSuccess: () => { refresh(); setOpen(false); message.success(editing ? "Đã cập nhật người dùng" : "Đã tạo người dùng"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const resetMutation = useMutation({
    mutationFn: (value: { password: string }) => securityAdminApi.updateUser(resetUser!.id, {
      displayName: resetUser!.displayName,
      email: resetUser!.email,
      status: resetUser!.status,
      newPassword: value.password,
      mustChangePassword: true,
      rowVersion: resetUser!.rowVersion,
    }),
    onSuccess: () => { setResetUser(undefined); resetForm.resetFields(); message.success("Đã đặt lại mật khẩu"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const users = useMemo(() => {
    const normalized = keyword.trim().toLocaleLowerCase("vi");
    const items = usersQuery.data?.items ?? [];
    return normalized ? items.filter((user) => `${user.username} ${user.displayName} ${user.email ?? ""}`.toLocaleLowerCase("vi").includes(normalized)) : items;
  }, [keyword, usersQuery.data]);

  const showForm = (user?: ManagedUser) => {
    setEditing(user);
    const roleIds = user?.roles.map((code) => rolesQuery.data?.find((role) => role.code === code)?.id).filter((id): id is string => Boolean(id)) ?? [];
    form.setFieldsValue(user ? { ...user, roleIds, password: undefined } : { username: "", displayName: "", roleIds: [], status: "ACTIVE", mustChangePassword: true });
    setOpen(true);
  };

  if (usersQuery.isLoading || rolesQuery.isLoading) return <PageLoading />;
  if (usersQuery.isError) return <PageError message={getErrorMessage(usersQuery.error)} onRetry={() => void usersQuery.refetch()} />;

  return (
    <div className="page-stack">
      <div className="toolbar surface-card management-toolbar">
        <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tài khoản, họ tên hoặc email..." value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        <Can permission={Permissions.USER_MANAGE}><Button type="primary" icon={<PlusOutlined />} onClick={() => showForm()}>Thêm người dùng</Button></Can>
      </div>
      <Card styles={{ body: { padding: 0 } }}>
        <Table rowKey="id" dataSource={users} pagination={{ pageSize: 20 }} scroll={{ x: 850 }} columns={[
          { title: "Người dùng", key: "user", render: (_, user) => <div className="security-main-cell"><strong>{user.displayName}</strong><small>{user.username}{user.email ? ` · ${user.email}` : ""}</small></div> },
          { title: "Vai trò", dataIndex: "roles", render: (roles: string[]) => <Space size={[4, 4]} wrap>{roles?.map((role) => <Tag key={role}>{role}</Tag>)}</Space> },
          { title: "Trạng thái", dataIndex: "status", width: 150, render: (status: string) => <Tag color={status === "ACTIVE" ? "green" : status === "LOCKED" ? "red" : "default"}>{status === "ACTIVE" ? "Đang hoạt động" : status === "LOCKED" ? "Đã khóa" : "Tạm ngưng"}</Tag> },
          { title: "Thao tác", key: "actions", width: 220, render: (_, user) => <Can permission={Permissions.USER_MANAGE}><Space><Button type="text" icon={<EditOutlined />} onClick={() => showForm(user)}>Sửa</Button><Button type="text" icon={<UnlockOutlined />} onClick={() => setResetUser(user)}>Đặt mật khẩu</Button></Space></Can> },
        ]} />
      </Card>
      <Modal title={editing ? "Cập nhật người dùng" : "Thêm người dùng"} open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={saveMutation.isPending} okText="Lưu người dùng">
        <Form<UserFormValue> form={form} layout="vertical" onFinish={(value) => saveMutation.mutate(value)} requiredMark="optional">
          <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: "Nhập tên đăng nhập" }, { pattern: /^[A-Za-z0-9@._-]{3,100}$/, message: "Dùng 3-100 ký tự chữ, số, @, dấu chấm, gạch ngang hoặc gạch dưới" }]}><Input disabled={Boolean(editing)} autoComplete="off" /></Form.Item>
          <Form.Item name="displayName" label="Họ và tên" rules={[{ required: true, message: "Nhập họ và tên" }]}><Input /></Form.Item>
          <Form.Item name="email" label="Email" rules={[{ type: "email", message: "Email chưa đúng định dạng" }]}><Input /></Form.Item>
          {!editing && <Form.Item name="password" label="Mật khẩu ban đầu" rules={[{ required: true, message: "Nhập mật khẩu ban đầu" }, { min: 8, message: "Mật khẩu cần ít nhất 8 ký tự" }]}><Input.Password autoComplete="new-password" /></Form.Item>}
          <Form.Item name="roleIds" label="Vai trò" rules={[{ required: true, message: "Chọn ít nhất một vai trò" }]}><Select disabled={editing?.id === currentUser?.id} mode="multiple" optionFilterProp="label" options={(rolesQuery.data ?? []).filter((role) => role.active).map((role) => ({ value: role.id, label: role.name }))} /></Form.Item>
          <Form.Item name="status" label="Trạng thái"><Select disabled={editing?.id === currentUser?.id} options={[{ value: "ACTIVE", label: "Đang hoạt động" }, { value: "INACTIVE", label: "Tạm ngưng" }, { value: "LOCKED", label: "Đã khóa" }]} /></Form.Item>
          <Form.Item name="mustChangePassword" label="Yêu cầu đổi mật khẩu ở lần đăng nhập tiếp theo" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>
      <Modal title={`Đặt lại mật khẩu · ${resetUser?.username ?? ""}`} open={Boolean(resetUser)} onCancel={() => setResetUser(undefined)} onOk={() => resetForm.submit()} confirmLoading={resetMutation.isPending} okText="Đặt lại mật khẩu">
        <Form form={resetForm} layout="vertical" onFinish={(value) => resetMutation.mutate(value)}><Form.Item name="password" label="Mật khẩu mới" rules={[{ required: true, message: "Nhập mật khẩu mới" }, { min: 8, message: "Mật khẩu cần ít nhất 8 ký tự" }]}><Input.Password autoComplete="new-password" /></Form.Item></Form>
      </Modal>
    </div>
  );
}
