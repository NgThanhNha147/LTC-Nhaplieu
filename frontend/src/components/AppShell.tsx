import {
  AppstoreOutlined,
  DatabaseOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  TagsOutlined,
} from "@ant-design/icons";
import { Button, Drawer, Layout, Menu } from "antd";
import { useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

const { Header, Sider, Content } = Layout;

export function AppShell() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const inTemplates = location.pathname.startsWith("/templates");
  const inLookups = location.pathname.startsWith("/lookups");
  const contextTitle = inLookups ? "Danh mục dùng chung" : inTemplates ? "Quản trị biểu mẫu" : "Quản lý hồ sơ";
  const selectedKey = inLookups ? "lookups" : inTemplates ? "templates" : "records";

  const goToTemplates = () => {
    navigate("/templates");
    setMobileOpen(false);
  };

  const goToLookups = () => {
    navigate("/lookups");
    setMobileOpen(false);
  };

  const goToWorkspaces = () => {
    navigate("/workspaces");
    setMobileOpen(false);
  };

  const operationItems = [
    { key: "records", icon: <DatabaseOutlined />, label: "Quản lý hồ sơ", onClick: goToWorkspaces },
  ];
  const administrationItems = [
    { key: "templates", icon: <AppstoreOutlined />, label: "Quản trị biểu mẫu", onClick: goToTemplates },
    { key: "lookups", icon: <TagsOutlined />, label: "Danh mục dùng chung", onClick: goToLookups },
  ];

  const navigation = (compact = false) => (
    <>
      <div className={`brand ${compact ? "brand-mobile" : ""}`} onClick={goToWorkspaces}>
        {!compact && collapsed ? (
          <div className="brand-monogram">LTC</div>
        ) : (
          <div className="brand-logo"><img src="/brand/ltc-logo.jpg" alt="LTC" /></div>
        )}
        {(!collapsed || compact) && <div className="brand-product"><strong>Hồ sơ số</strong><span>HỆ THỐNG NHẬP LIỆU ĐỘNG</span></div>}
      </div>
      {(!collapsed || compact) && <div className="workspace-label">NGHIỆP VỤ</div>}
      <Menu mode="inline" selectedKeys={[selectedKey]} items={operationItems} />
      {(!collapsed || compact) && <div className="workspace-label admin-label">QUẢN TRỊ HỆ THỐNG</div>}
      <Menu mode="inline" selectedKeys={[selectedKey]} items={administrationItems} />
      <div className="sider-footnote">
        <span className="status-dot" />
        {(!collapsed || compact) && <span>Hệ thống đang hoạt động</span>}
      </div>
    </>
  );

  return (
    <Layout className={`app-layout ${collapsed ? "sidebar-collapsed" : ""}`}>
      <Sider className="app-sider" width={250} collapsedWidth={78} collapsed={collapsed} trigger={null}>
        {navigation()}
      </Sider>
      <Drawer className="mobile-nav" placement="left" width={288} open={mobileOpen} onClose={() => setMobileOpen(false)} closable={false}>
        {navigation(true)}
      </Drawer>
      <Layout className="app-main-layout">
        <Header className="app-header">
          <Button
            className="desktop-menu-toggle"
            type="text"
            aria-label={collapsed ? "Mở menu" : "Thu gọn menu"}
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed((value) => !value)}
          />
          <Button className="mobile-menu-toggle" type="text" aria-label="Mở điều hướng" icon={<MenuOutlined />} onClick={() => setMobileOpen(true)} />
          <div className="header-context">
            <strong>{contextTitle}</strong>
          </div>
        </Header>
        <Content className="app-content"><Outlet /></Content>
      </Layout>
    </Layout>
  );
}
