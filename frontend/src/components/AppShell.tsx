import {
  AppstoreOutlined,
  AuditOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  SafetyCertificateOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
  FolderOpenOutlined,
  FormOutlined,
  CheckCircleOutlined,
  HomeOutlined,
} from "@ant-design/icons";
import { Avatar, Button, Drawer, Dropdown, Layout, Menu, type MenuProps } from "antd";
import { useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";
import type { AuthMenu } from "../auth/types";

const { Header, Sider, Content } = Layout;
type MenuItem = Required<MenuProps>["items"][number];

export function AppShell() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, hasPermission, securityEnabled } = useAuth();
  const inTemplates = location.pathname.startsWith("/templates");
  const inLookups = location.pathname.startsWith("/lookups");
  const inUsers = location.pathname.startsWith("/admin/users");
  const inRoles = location.pathname.startsWith("/admin/roles");
  const inAudit = location.pathname.startsWith("/admin/audit");
  const inBackups = location.pathname.startsWith("/admin/backups");
  const inWorkspaces = location.pathname.startsWith("/workspaces");
  const inBatches = location.pathname.startsWith("/batches") || location.pathname.startsWith("/my-batches");
  const inWorkQueue = location.pathname.startsWith("/my-work") || location.pathname.startsWith("/work-items");
  const inApprovals = location.pathname.startsWith("/approvals");
  const inOverview = location.pathname.startsWith("/overview");
  const contextTitle = inOverview ? "Tổng quan" : inApprovals ? "Phê duyệt hồ sơ" : inWorkQueue ? "Việc của tôi" : inWorkspaces ? "Mẫu hồ sơ" : inBatches ? "Đợt hồ sơ" : inAudit ? "Nhật ký hệ thống" : inBackups ? "Sao lưu và phục hồi" : inUsers ? "Quản lý người dùng" : inRoles ? "Vai trò và quyền" : inLookups ? "Danh mục dùng chung" : inTemplates ? "Cấu hình biểu mẫu" : "Mẫu hồ sơ";
  const fallbackSelectedKey = inOverview ? "overview" : inApprovals ? "approvals" : inWorkQueue ? "my-work" : inWorkspaces ? "records" : inBatches ? "records" : inAudit ? "audit" : inBackups ? "backups" : inUsers ? "users" : inRoles ? "roles" : inLookups ? "lookups" : inTemplates ? "templates" : "records";
  const assignedMenu = user?.menus ?? [];
  const activeAssignedMenu = assignedMenu.find((item) => item.path && location.pathname.startsWith(item.path));
  const selectedKey = securityEnabled ? activeAssignedMenu?.code.toLowerCase() ?? "" : fallbackSelectedKey;

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
  const goToHome = () => {
    navigate("/");
    setMobileOpen(false);
  };

  const operationItems = [
    hasPermission(Permissions.BATCH_VIEW) ? { key: "overview", icon: <HomeOutlined />, label: "Tổng quan", onClick: () => { navigate("/overview"); setMobileOpen(false); } } : null,
    hasPermission(Permissions.RECORD_VIEW) ? { key: "records", icon: <DatabaseOutlined />, label: "Mẫu hồ sơ", onClick: goToWorkspaces } : null,
    hasPermission(Permissions.BATCH_VIEW) ? { key: "my-work", icon: <FormOutlined />, label: "Việc của tôi", onClick: () => { navigate("/my-work"); setMobileOpen(false); } } : null,
    hasPermission(Permissions.RECORD_APPROVE) ? { key: "approvals", icon: <CheckCircleOutlined />, label: "Chờ duyệt", onClick: () => { navigate("/approvals"); setMobileOpen(false); } } : null,
  ].filter(Boolean);
  const administrationItems = [
    hasPermission(Permissions.TEMPLATE_VIEW) ? { key: "templates", icon: <AppstoreOutlined />, label: "Cấu hình biểu mẫu", onClick: goToTemplates } : null,
    hasPermission(Permissions.LOOKUP_VIEW) ? { key: "lookups", icon: <TagsOutlined />, label: "Danh mục dùng chung", onClick: goToLookups } : null,
    hasPermission(Permissions.USER_VIEW) ? { key: "users", icon: <TeamOutlined />, label: "Người dùng", onClick: () => { navigate("/admin/users"); setMobileOpen(false); } } : null,
    hasPermission(Permissions.ROLE_MANAGE) ? { key: "roles", icon: <SafetyCertificateOutlined />, label: "Vai trò và quyền", onClick: () => { navigate("/admin/roles"); setMobileOpen(false); } } : null,
    hasPermission(Permissions.AUDIT_VIEW) ? { key: "audit", icon: <AuditOutlined />, label: "Nhật ký hệ thống", onClick: () => { navigate("/admin/audit"); setMobileOpen(false); } } : null,
    hasPermission(Permissions.BACKUP_VIEW) ? { key: "backups", icon: <CloudServerOutlined />, label: "Sao lưu và phục hồi", onClick: () => { navigate("/admin/backups"); setMobileOpen(false); } } : null,
  ].filter(Boolean);
  const menuIcon = (menu: AuthMenu) => {
    const icon = menu.icon || menu.code;
    if (/database|record/i.test(icon)) return <DatabaseOutlined />;
    if (/appstore|template/i.test(icon)) return <AppstoreOutlined />;
    if (/tag|lookup/i.test(icon)) return <TagsOutlined />;
    if (/team|user/i.test(icon)) return <TeamOutlined />;
    if (/safety|role/i.test(icon)) return <SafetyCertificateOutlined />;
    if (/audit/i.test(icon)) return <AuditOutlined />;
    if (/cloud|backup/i.test(icon)) return <CloudServerOutlined />;
    if (/approval|approve|check/i.test(icon)) return <CheckCircleOutlined />;
    if (/home|overview|dashboard/i.test(icon)) return <HomeOutlined />;
    if (/batch|folder/i.test(icon)) return <FolderOpenOutlined />;
    if (/entry|work|form/i.test(icon)) return <FormOutlined />;
    return <AppstoreOutlined />;
  };
  const mapAssignedMenu = (menu: AuthMenu): MenuItem => {
    const children = menu.children?.map(mapAssignedMenu) ?? [];
    const item = {
      key: menu.code.toLowerCase(),
      icon: menuIcon(menu),
      label: menu.label,
    };
    if (children.length > 0) return { ...item, children };
    return {
      ...item,
      onClick: menu.path ? () => { navigate(menu.path); setMobileOpen(false); } : undefined,
    };
  };
  const assignedItems = [...assignedMenu].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)).map(mapAssignedMenu);

  const navigation = (compact = false) => (
    <>
      <div className={`brand ${compact ? "brand-mobile" : ""}`} onClick={goToHome}>
        {!compact && collapsed ? (
          <div className="brand-monogram">LTC</div>
        ) : (
          <div className="brand-logo"><img src="/brand/ltc-logo.jpg" alt="LTC" /></div>
        )}
        {(!collapsed || compact) && <div className="brand-product"><strong>Hồ sơ số</strong><span>HỆ THỐNG NHẬP LIỆU ĐỘNG</span></div>}
      </div>
      {securityEnabled ? <>
        {(!collapsed || compact) && <div className="workspace-label">CHỨC NĂNG ĐƯỢC CẤP</div>}
        <Menu mode="inline" selectedKeys={[selectedKey]} items={assignedItems} />
      </> : <>
        {(!collapsed || compact) && <div className="workspace-label">NGHIỆP VỤ</div>}
        <Menu mode="inline" selectedKeys={[selectedKey]} items={operationItems} />
        {(!collapsed || compact) && <div className="workspace-label admin-label">QUẢN TRỊ HỆ THỐNG</div>}
        <Menu mode="inline" selectedKeys={[selectedKey]} items={administrationItems} />
      </>}
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
          <Dropdown trigger={["click"]} menu={{ items: [
            { key: "identity", disabled: true, label: <div className="user-menu-identity"><strong>{user?.displayName}</strong><small>{user?.username}</small></div> },
            { type: "divider" },
            { key: "logout", icon: <LogoutOutlined />, label: "Đăng xuất", disabled: !securityEnabled, onClick: () => void logout() },
          ] }}>
            <Button className="header-user-button" type="text"><Avatar size={32} icon={<UserOutlined />} /><span>{user?.displayName || "Người dùng"}</span></Button>
          </Dropdown>
        </Header>
        <Content className="app-content"><Outlet /></Content>
      </Layout>
    </Layout>
  );
}
