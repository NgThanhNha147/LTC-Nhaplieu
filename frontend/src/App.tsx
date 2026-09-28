import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { PageLoading } from "./components/ApiState";

const TemplatesPage = lazy(() => import("./pages/TemplatesPage").then((module) => ({ default: module.TemplatesPage })));
const TemplateDesignerPage = lazy(() => import("./pages/TemplateDesignerPage").then((module) => ({ default: module.TemplateDesignerPage })));
const LookupSourcesPage = lazy(() => import("./pages/LookupSourcesPage").then((module) => ({ default: module.LookupSourcesPage })));
const WorkspacesPage = lazy(() => import("./pages/WorkspacesPage").then((module) => ({ default: module.WorkspacesPage })));
const RecordsPage = lazy(() => import("./pages/RecordsPage").then((module) => ({ default: module.RecordsPage })));
const DataEntryPage = lazy(() => import("./pages/DataEntryPage").then((module) => ({ default: module.DataEntryPage })));

export default function App() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/workspaces" replace />} />
          <Route path="/templates" element={<TemplatesPage />} />
          <Route path="/templates/:templateId/designer" element={<TemplateDesignerPage />} />
          <Route path="/lookups" element={<LookupSourcesPage />} />
          <Route path="/workspaces" element={<WorkspacesPage />} />
          <Route path="/workspaces/:templateCode" element={<RecordsPage />} />
          <Route path="/workspaces/:templateCode/new" element={<DataEntryPage />} />
          <Route path="/workspaces/:templateCode/records/:recordId" element={<DataEntryPage />} />
          <Route path="*" element={<Navigate to="/workspaces" replace />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
