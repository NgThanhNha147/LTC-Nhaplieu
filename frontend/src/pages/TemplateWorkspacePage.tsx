import { ArrowLeftOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, Tabs, Tooltip } from "antd";
import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import { templateApi } from "../api/templates";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";
import { PageError, PageLoading } from "../components/ApiState";
import { BatchesPage } from "./BatchesPage";
import { RecordsPage } from "./RecordsPage";

export function TemplateWorkspacePage() {
  const { templateCode = "" } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { hasPermission } = useAuth();
  const canViewBatches = hasPermission(Permissions.BATCH_VIEW);
  const canViewAllRecords = hasPermission(Permissions.RECORD_EXPORT);
  const requestedTab = searchParams.get("tab");
  const activeTab = requestedTab === "records" && canViewAllRecords ? "records" : "batches";

  const templatesQuery = useQuery({ queryKey: ["templates", "workspace"], queryFn: () => templateApi.list(0, 200) });
  const template = useMemo(
    () => templatesQuery.data?.items.find((item) => item.code.toLocaleLowerCase() === templateCode.toLocaleLowerCase()),
    [templateCode, templatesQuery.data?.items],
  );

  if (templatesQuery.isLoading) return <PageLoading />;
  if (templatesQuery.isError) return <PageError message={getErrorMessage(templatesQuery.error)} onRetry={() => void templatesQuery.refetch()} />;
  if (!template) return <PageError message="Không tìm thấy mẫu hồ sơ hoặc mẫu chưa sẵn sàng sử dụng." />;

  const tabs = [
    ...(canViewBatches ? [{ key: "batches", label: "Đợt hồ sơ", children: <BatchesPage template={template} startCreate={searchParams.get("create") === "1"} /> }] : []),
    ...(canViewAllRecords ? [{ key: "records", label: "Tất cả hồ sơ", children: <RecordsPage /> }] : []),
  ];

  return <div className="page-stack template-workspace-page">
    <Card className="template-workspace-tabs" styles={{ body: { padding: 0 } }}>
      <Tabs
        activeKey={activeTab}
        items={tabs}
        onChange={(key) => setSearchParams({ tab: key })}
        destroyOnHidden
        tabBarExtraContent={{
          left: <Tooltip title="Về danh sách mẫu"><Button className="template-tabs-back" type="text" aria-label="Về danh sách mẫu" icon={<ArrowLeftOutlined />} onClick={() => navigate("/workspaces")} /></Tooltip>,
        }}
      />
    </Card>
  </div>;
}
