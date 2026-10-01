import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from "@ant-design/icons";
import { Alert, App, Button, Form, Input } from "antd";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import { useAuth } from "../auth/AuthProvider";

interface LoginFormValue {
  username: string;
  password: string;
}

export function LoginPage() {
  const { message } = App.useApp();
  const { authenticated, login, securityEnabled } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = (location.state as { from?: string } | null)?.from || "/";

  useEffect(() => {
    if (authenticated) navigate(redirectTo, { replace: true });
  }, [authenticated, navigate, redirectTo]);

  const submit = async (values: LoginFormValue) => {
    setSubmitting(true);
    try {
      await login(values.username.trim(), values.password);
      message.success("Đăng nhập thành công");
      navigate(redirectTo, { replace: true });
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="login-page">
      <section className="login-story">
        <div className="login-brand"><img src="/brand/ltc-logo.jpg" alt="LTC" /></div>
        <div>
          <span className="login-kicker">NỀN TẢNG HỒ SƠ SỐ</span>
          <h1>Nhập liệu nhất quán.<br />Kiểm soát đúng quyền.</h1>
          <p>Quản lý biểu mẫu, dữ liệu và lịch sử thao tác trên một hệ thống thống nhất.</p>
        </div>
        <div className="login-security-note"><SafetyCertificateOutlined /><span>Kết nối được bảo vệ và mọi thao tác quan trọng đều được ghi nhận.</span></div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <h2>Đăng nhập hệ thống</h2>
          <p>Sử dụng tài khoản được quản trị viên cấp.</p>
          {!securityEnabled && <Alert type="info" showIcon message="Chế độ demo đang bật" description="Hệ thống bỏ qua đăng nhập khi VITE_SECURITY_ENABLED=false." />}
          <Form<LoginFormValue> layout="vertical" onFinish={submit} initialValues={{ username: "" }} requiredMark={false}>
            <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: "Nhập tên đăng nhập" }]}>
              <Input autoFocus autoComplete="username" prefix={<UserOutlined />} placeholder="Tên đăng nhập" />
            </Form.Item>
            <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: "Nhập mật khẩu" }]}>
              <Input.Password autoComplete="current-password" prefix={<LockOutlined />} placeholder="Mật khẩu" />
            </Form.Item>
            <Button block type="primary" htmlType="submit" loading={submitting}>Đăng nhập</Button>
          </Form>
        </div>
      </section>
    </main>
  );
}
