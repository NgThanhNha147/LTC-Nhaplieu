import {
  DatabaseOutlined,
  FileAddOutlined,
  RightOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, Col, Empty, Input, Row } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import { templateApi } from "../api/templates";
import { PageError, PageLoading } from "../components/ApiState";
import { Can } from "../auth/Can";
import { Permissions } from "../auth/permissions";

export function WorkspacesPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const query = useQuery({ queryKey: ["templates"], queryFn: () => templateApi.list(0, 100) });

  const publishedTemplates = useMemo(
    () => (query.data?.items ?? []).filter((template) => template.status === "PUBLISHED" && !template.code.startsWith("SMOKE_")),
    [query.data],
  );
  const filteredTemplates = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase("vi");
    if (!keyword) return publishedTemplates;
    return publishedTemplates.filter((template) =>
      `${template.code} ${template.name} ${template.description ?? ""}`.toLocaleLowerCase("vi").includes(keyword),
    );
  }, [publishedTemplates, search]);

  if (query.isLoading) return <PageLoading />;
  if (query.isError) return <PageError message={getErrorMessage(query.error)} onRetry={() => void query.refetch()} />;

  return (
    <div className="page-stack">
      <div className="toolbar surface-card workspace-toolbar">
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="Tìm mẫu hồ sơ..."
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {filteredTemplates.length === 0 ? (
        <div className="surface-card workspace-empty">
          <Empty description={publishedTemplates.length ? "Không tìm thấy mẫu hồ sơ phù hợp" : "Chưa có mẫu hồ sơ nào sẵn sàng sử dụng"} />
        </div>
      ) : (
        <Row gutter={[18, 18]}>
          {filteredTemplates.map((template) => (
            <Col xs={24} md={12} xl={8} key={template.id}>
              <Card className="workspace-card">
                <div className="workspace-card-icon"><DatabaseOutlined /></div>
                <div className="workspace-card-code">{template.code} · PHIÊN BẢN {template.currentVersion ?? "—"}</div>
                <h2>{template.name}</h2>
                <p>{template.description || "Tiếp nhận theo đợt, nhập liệu và phê duyệt hồ sơ theo mẫu này."}</p>
                <div className="workspace-card-actions">
                  <Can permission={Permissions.BATCH_CREATE}><Button icon={<FileAddOutlined />} onClick={() => navigate(`/workspaces/${template.code}?tab=batches&create=1`)}>Tạo đợt hồ sơ</Button></Can>
                  <Button type="primary" icon={<DatabaseOutlined />} onClick={() => navigate(`/workspaces/${template.code}`)}>
                    Mở mẫu <RightOutlined />
                  </Button>
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </div>
  );
}
