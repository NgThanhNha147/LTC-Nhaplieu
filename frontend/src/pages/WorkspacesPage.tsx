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
          placeholder="Tìm loại hồ sơ..."
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {filteredTemplates.length === 0 ? (
        <div className="surface-card workspace-empty">
          <Empty description={publishedTemplates.length ? "Không tìm thấy loại hồ sơ phù hợp" : "Chưa có loại hồ sơ nào sẵn sàng sử dụng"} />
        </div>
      ) : (
        <Row gutter={[18, 18]}>
          {filteredTemplates.map((template) => (
            <Col xs={24} md={12} xl={8} key={template.id}>
              <Card className="workspace-card">
                <div className="workspace-card-icon"><DatabaseOutlined /></div>
                <h2>{template.name}</h2>
                <p>{template.description || "Nhập và quản lý dữ liệu hồ sơ."}</p>
                <div className="workspace-card-actions">
                  <Button icon={<DatabaseOutlined />} onClick={() => navigate(`/workspaces/${template.code}`)}>Danh sách hồ sơ</Button>
                  <Button type="primary" icon={<FileAddOutlined />} onClick={() => navigate(`/workspaces/${template.code}/new`)}>
                    Tạo hồ sơ mới <RightOutlined />
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
