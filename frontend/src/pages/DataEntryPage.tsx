import { ArrowLeftOutlined, CheckOutlined, EditOutlined, EyeOutlined, LeftOutlined, RightOutlined, SaveOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Space, Spin, Tag, Tooltip } from "antd";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import { documentApi } from "../api/documents";
import { recordApi } from "../api/records";
import { templateApi } from "../api/templates";
import { DocumentViewer } from "../components/DocumentViewer";
import { DynamicFormRenderer } from "../components/DynamicFormRenderer";
import { PageError } from "../components/ApiState";
import { useAuth } from "../auth/AuthProvider";
import { Can } from "../auth/Can";
import { Permissions } from "../auth/permissions";

export function DataEntryPage() {
  const { templateCode = "", recordId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { hasPermission } = useAuth();
  const canUpdate = hasPermission(Permissions.RECORD_UPDATE);
  const compareMode = Boolean(recordId) && (searchParams.get("mode") === "compare" || !canUpdate);
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [documentUrl, setDocumentUrl] = useState<string>();
  const [sourceDocumentId, setSourceDocumentId] = useState<string>();
  const { control, reset, getValues, handleSubmit, setFocus, formState: { isDirty } } = useForm<Record<string, unknown>>();
  const formQuery = useQuery({ queryKey: ["form", templateCode], queryFn: () => templateApi.form(templateCode), enabled: Boolean(templateCode) });
  const recordQuery = useQuery({ queryKey: ["record", templateCode, recordId], queryFn: () => recordApi.get(templateCode, recordId!), enabled: Boolean(recordId) });
  const compareRecordsQuery = useQuery({
    queryKey: ["compare-records", templateCode],
    queryFn: () => recordApi.search(templateCode, { filters: [], sort: [{ field: "createdAt", direction: "DESC" }], page: 0, size: 200 }),
    enabled: compareMode,
  });

  useEffect(() => {
    if (recordQuery.data) {
      reset(recordQuery.data.data);
      setDocumentUrl(recordQuery.data.sourceDocumentId ? documentApi.contentUrl(recordQuery.data.sourceDocumentId) : undefined);
      setSourceDocumentId(recordQuery.data.sourceDocumentId);
    }
  }, [recordQuery.data, reset]);

  const saveMutation = useMutation({
    mutationFn: ({ values, status }: { values: Record<string, unknown>; status: string }) => {
      const payload = { data: values, status, sourceDocumentId, rowVersion: recordQuery.data?.rowVersion };
      return recordId ? recordApi.update(templateCode, recordId, payload) : recordApi.create(templateCode, payload);
    },
    onSuccess: (record) => {
      queryClient.setQueryData(["record", templateCode, record.id], record);
      reset(record.data);
      setSourceDocumentId(record.sourceDocumentId);
      setDocumentUrl(record.sourceDocumentId ? documentApi.contentUrl(record.sourceDocumentId) : undefined);
      message.success(record.recordStatus === "COMPLETED" ? "Đã hoàn thành bản ghi" : "Đã lưu bản nháp");
      navigate(`/workspaces/${templateCode}/records/${record.id}`, { replace: true });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  if (formQuery.isLoading || (recordId && recordQuery.isLoading)) return <div className="center-spin"><Spin size="large" /></div>;
  if (formQuery.isError) return <PageError message={getErrorMessage(formQuery.error)} onRetry={() => void formQuery.refetch()} />;
  if (recordQuery.isError) return <PageError message={getErrorMessage(recordQuery.error)} onRetry={() => void recordQuery.refetch()} />;
  const metadata = formQuery.data!;
  const submitCompleted = handleSubmit(
    (values) => {
      const invalidRule = (metadata.rules ?? []).find((rule) => rule.ruleType === "AT_LEAST_ONE_FILLED"
        && !rule.fieldCodes.some((code) => values[code] !== undefined && values[code] !== null && String(values[code]).trim() !== ""));
      if (invalidRule) {
        message.error(invalidRule.errorMessage ?? "Cần nhập ít nhất một trong các trường liên quan.");
        const first = invalidRule.fieldCodes[0];
        if (first) { document.getElementById(first)?.scrollIntoView({ behavior: "smooth", block: "center" }); setFocus(first); }
        return;
      }
      saveMutation.mutate({ values, status: "COMPLETED" });
    },
    (errors) => {
      const firstField = Object.keys(errors)[0];
      const field = metadata.groups.flatMap((group) => group.fields ?? []).find((item) => item.fieldCode === firstField);
      message.error(firstField ? `Vui lòng kiểm tra trường "${field?.label ?? firstField}"` : "Vui lòng kiểm tra lại dữ liệu đã nhập");
      if (firstField) {
        requestAnimationFrame(() => {
          document.getElementById(firstField)?.scrollIntoView({ behavior: "smooth", block: "center" });
          setFocus(firstField);
        });
      }
    },
  );
  const saveDraft = () => saveMutation.mutate({ values: getValues(), status: "DRAFT" });
  const editUrl = `/workspaces/${templateCode}/records/${recordId}`;
  const compareRecords = compareRecordsQuery.data?.items ?? [];
  const compareIndex = compareRecords.findIndex((record) => record.id === recordId);
  const previousRecord = compareIndex > 0 ? compareRecords[compareIndex - 1] : undefined;
  const nextRecord = compareIndex >= 0 && compareIndex < compareRecords.length - 1 ? compareRecords[compareIndex + 1] : undefined;
  const comparePosition = compareIndex >= 0 ? `${compareIndex + 1} / ${compareRecords.length}` : "— / —";
  const openCompareRecord = (id?: string) => {
    if (id) navigate(`/workspaces/${templateCode}/records/${id}?mode=compare`);
  };

  return (
    <div className={`entry-page ${compareMode ? "compare-mode" : ""}`}>
      <div className="entry-topbar">
        <div className="entry-title">
          <Tooltip title="Về danh sách hồ sơ">
            <Button
              type="text"
              aria-label="Về danh sách hồ sơ"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate(`/workspaces/${templateCode}`)}
            />
          </Tooltip>
          <div><h1>{metadata.templateName}</h1></div>
          {compareMode ? <Tag icon={<EyeOutlined />} color="cyan">Chỉ đọc</Tag> : recordId ? <Tag color="blue">Đang chỉnh sửa</Tag> : <Tag color="green">Hồ sơ mới</Tag>}
        </div>
        {compareMode ? (
          <Space className="compare-actions" size={6}>
            <Button className="compare-nav-button" type="text" icon={<LeftOutlined />} disabled={!previousRecord} title="Hồ sơ trước" aria-label="Hồ sơ trước" onClick={() => openCompareRecord(previousRecord?.id)} />
            <span className="compare-position">{comparePosition}</span>
            <Button className="compare-nav-button" type="text" icon={<RightOutlined />} disabled={!nextRecord} title="Hồ sơ sau" aria-label="Hồ sơ sau" onClick={() => openCompareRecord(nextRecord?.id)} />
            <Can permission={Permissions.RECORD_UPDATE}><Button type="primary" icon={<EditOutlined />} onClick={() => navigate(editUrl)}>Chỉnh sửa</Button></Can>
          </Space>
        ) : (
          <Space wrap><Button icon={<SaveOutlined />} loading={saveMutation.isPending} onClick={saveDraft}>Lưu nháp</Button><Can permission={Permissions.RECORD_COMPLETE}><Button type="primary" icon={<CheckOutlined />} loading={saveMutation.isPending} onClick={() => void submitCompleted()}>Hoàn tất nhập liệu</Button></Can></Space>
        )}
      </div>
      <div className="entry-workspace">
        <DocumentViewer initialUrl={documentUrl} onDocumentIdChange={setSourceDocumentId} readOnly={compareMode} />
        <main className="form-pane">
          <DynamicFormRenderer metadata={metadata} control={control} disabled={compareMode} />
          {compareMode ? (
            <div className="mobile-entry-actions compare-mobile-action">
              <div className="compare-mobile-nav">
                <Button icon={<LeftOutlined />} disabled={!previousRecord} onClick={() => openCompareRecord(previousRecord?.id)}>Hồ sơ trước</Button>
                <span>{comparePosition}</span>
                <Button icon={<RightOutlined />} disabled={!nextRecord} onClick={() => openCompareRecord(nextRecord?.id)}>Hồ sơ sau</Button>
              </div>
              <Can permission={Permissions.RECORD_UPDATE}><Button block type="primary" icon={<EditOutlined />} onClick={() => navigate(editUrl)}>Chỉnh sửa hồ sơ</Button></Can>
            </div>
          ) : (
            <div className="mobile-entry-actions"><Button block icon={<SaveOutlined />} loading={saveMutation.isPending} onClick={saveDraft}>Lưu nháp</Button><Can permission={Permissions.RECORD_COMPLETE}><Button block type="primary" icon={<CheckOutlined />} loading={saveMutation.isPending} onClick={() => void submitCompleted()}>Hoàn tất nhập liệu</Button></Can></div>
          )}
          {!compareMode && isDirty && <div className="unsaved-indicator">Có thay đổi chưa lưu</div>}
        </main>
      </div>
    </div>
  );
}
