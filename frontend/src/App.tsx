import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useParams } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { PageLoading } from "./components/ApiState";
import { PermissionRoute, ProtectedRoute } from "./auth/ProtectedRoute";
import { Permissions } from "./auth/permissions";
import { useAuth } from "./auth/AuthProvider";

const TemplatesPage = lazy(() => import("./pages/TemplatesPage").then((module) => ({ default: module.TemplatesPage })));
const TemplateDesignerPage = lazy(() => import("./pages/TemplateDesignerPage").then((module) => ({ default: module.TemplateDesignerPage })));
const LookupSourcesPage = lazy(() => import("./pages/LookupSourcesPage").then((module) => ({ default: module.LookupSourcesPage })));
const WorkspacesPage = lazy(() => import("./pages/WorkspacesPage").then((module) => ({ default: module.WorkspacesPage })));
const OverviewPage = lazy(() => import("./pages/OverviewPage").then((module) => ({ default: module.OverviewPage })));
const TemplateWorkspacePage = lazy(() => import("./pages/TemplateWorkspacePage").then((module) => ({ default: module.TemplateWorkspacePage })));
const DataEntryPage = lazy(() => import("./pages/DataEntryPage").then((module) => ({ default: module.DataEntryPage })));
const LoginPage = lazy(() => import("./pages/LoginPage").then((module) => ({ default: module.LoginPage })));
const ForbiddenPage = lazy(() => import("./pages/ForbiddenPage").then((module) => ({ default: module.ForbiddenPage })));
const UsersPage = lazy(() => import("./pages/UsersPage").then((module) => ({ default: module.UsersPage })));
const RolesPage = lazy(() => import("./pages/RolesPage").then((module) => ({ default: module.RolesPage })));
const AuditPage = lazy(() => import("./pages/AuditPage").then((module) => ({ default: module.AuditPage })));
const BackupsPage = lazy(() => import("./pages/BackupsPage").then((module) => ({ default: module.BackupsPage })));
const BatchDetailPage = lazy(() => import("./pages/BatchDetailPage").then((module) => ({ default: module.BatchDetailPage })));
const BatchWorkItemPage = lazy(() => import("./pages/BatchWorkItemPage").then((module) => ({ default: module.BatchWorkItemPage })));
const WorkQueuePage = lazy(() => import("./pages/WorkQueuePage").then((module) => ({ default: module.WorkQueuePage })));

function AuthorizedHome() {
  const { hasPermission } = useAuth();
  const path = hasPermission(Permissions.BATCH_VIEW) ? "/overview"
    : hasPermission(Permissions.RECORD_VIEW) ? "/workspaces"
    : hasPermission(Permissions.TEMPLATE_VIEW) ? "/templates"
      : hasPermission(Permissions.USER_VIEW) ? "/admin/users"
        : hasPermission(Permissions.ROLE_MANAGE) ? "/admin/roles"
          : hasPermission(Permissions.AUDIT_VIEW) ? "/admin/audit"
            : hasPermission(Permissions.BACKUP_VIEW) ? "/admin/backups" : "/forbidden";
  return <Navigate to={path} replace />;
}

function LegacyRecordCreateRedirect() {
  const { templateCode = "" } = useParams();
  return <Navigate to={`/workspaces/${templateCode}?tab=batches`} replace />;
}

export default function App() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<AuthorizedHome />} />
            <Route path="/forbidden" element={<ForbiddenPage />} />
            <Route element={<PermissionRoute permission={Permissions.BATCH_VIEW} />}><Route path="/overview" element={<OverviewPage />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.TEMPLATE_VIEW} />}>
              <Route path="/templates" element={<TemplatesPage />} />
            </Route>
            <Route element={<PermissionRoute permission={Permissions.TEMPLATE_CONFIGURE} />}><Route path="/templates/:templateId/designer" element={<TemplateDesignerPage />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.LOOKUP_VIEW} />}><Route path="/lookups" element={<LookupSourcesPage />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.RECORD_VIEW} />}>
              <Route path="/workspaces" element={<WorkspacesPage />} />
              <Route path="/workspaces/:templateCode" element={<TemplateWorkspacePage />} />
              <Route path="/workspaces/:templateCode/new" element={<LegacyRecordCreateRedirect />} />
            </Route>
            <Route element={<PermissionRoute permission={Permissions.RECORD_EXPORT} />}><Route path="/workspaces/:templateCode/records/:recordId" element={<DataEntryPage />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.BATCH_VIEW} />}>
              <Route path="/my-batches" element={<Navigate to="/workspaces" replace />} />
              <Route path="/my-work" element={<WorkQueuePage />} />
              <Route path="/workspaces/:templateCode/batches/:batchId" element={<BatchDetailPage />} />
              <Route path="/batches/:batchId" element={<BatchDetailPage />} />
              <Route path="/work-items/:workItemId" element={<BatchWorkItemPage />} />
            </Route>
            <Route element={<PermissionRoute permission={Permissions.BATCH_CREATE} />}><Route path="/batches" element={<Navigate to="/workspaces" replace />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.RECORD_APPROVE} />}><Route path="/approvals" element={<WorkQueuePage approval />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.USER_VIEW} />}><Route path="/admin/users" element={<UsersPage />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.ROLE_MANAGE} />}><Route path="/admin/roles" element={<RolesPage />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.AUDIT_VIEW} />}><Route path="/admin/audit" element={<AuditPage />} /></Route>
            <Route element={<PermissionRoute permission={Permissions.BACKUP_VIEW} />}><Route path="/admin/backups" element={<BackupsPage />} /></Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
