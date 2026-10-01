import { App, Button, Form, Input, Modal } from "antd";
import { useState } from "react";
import { authApi } from "../api/auth";
import { getErrorMessage } from "../api/client";
import type { AuthUser } from "./types";

interface ChangePasswordValue { currentPassword: string; newPassword: string; confirmPassword: string }

export function ForcePasswordChange({ user, onChanged }: { user: AuthUser | null; onChanged: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<ChangePasswordValue>();
  const [submitting, setSubmitting] = useState(false);
  const submit = async (value: ChangePasswordValue) => {
    setSubmitting(true);
    try {
      await authApi.changePassword(value.currentPassword, value.newPassword);
      form.resetFields();
      message.success("Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
      onChanged();
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };
  return <Modal open={Boolean(user?.mustChangePassword)} closable={false} maskClosable={false} keyboard={false} footer={null} title="Bạn cần đổi mật khẩu">
    <p>Đây là mật khẩu được cấp ban đầu hoặc vừa được quản trị viên đặt lại. Hãy đổi mật khẩu trước khi tiếp tục sử dụng hệ thống.</p>
    <Form form={form} layout="vertical" onFinish={submit} requiredMark={false}>
      <Form.Item name="currentPassword" label="Mật khẩu hiện tại" rules={[{ required: true, message: "Nhập mật khẩu hiện tại" }]}><Input.Password autoComplete="current-password" /></Form.Item>
      <Form.Item name="newPassword" label="Mật khẩu mới" rules={[{ required: true, message: "Nhập mật khẩu mới" }, { min: 8, message: "Mật khẩu cần ít nhất 8 ký tự" }]}><Input.Password autoComplete="new-password" /></Form.Item>
      <Form.Item name="confirmPassword" label="Nhập lại mật khẩu mới" dependencies={["newPassword"]} rules={[{ required: true, message: "Nhập lại mật khẩu mới" }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue("newPassword") === value ? Promise.resolve() : Promise.reject(new Error("Mật khẩu nhập lại chưa khớp")); } })]}><Input.Password autoComplete="new-password" /></Form.Item>
      <Button block type="primary" htmlType="submit" loading={submitting}>Đổi mật khẩu và tiếp tục</Button>
    </Form>
  </Modal>;
}
