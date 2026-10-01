import {
  ArrowLeftOutlined, CheckOutlined, CloseOutlined, EditOutlined, LeftOutlined,
  RightOutlined, SaveOutlined, SendOutlined, UndoOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Alert, Button, Modal, Space, Spin, Tag, Tooltip } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useParams } from "react-router-dom";
import { batchApi, workItemApi } from "../api/batches";
import { getErrorMessage } from "../api/client";
import { documentApi } from "../api/documents";
import { templateApi } from "../api/templates";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";
import { PageError } from "../components/ApiState";
import { DocumentViewer } from "../components/DocumentViewer";
import { DynamicFormRenderer } from "../components/DynamicFormRenderer";
import { WorkflowStatusTag } from "../components/WorkflowStatusTag";

export function BatchWorkItemPage() {
  const { workItemId = "" } = useParams();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { hasPermission, user, securityEnabled } = useAuth();
  const queryClient = useQueryClient();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [reopenOpen, setReopenOpen] = useState(false);
  const [reopenReason, setReopenReason] = useState("");
  const { control, reset, getValues, handleSubmit, setFocus, formState: { isDirty } } = useForm<Record<string, unknown>>();
  const itemQuery = useQuery({ queryKey: ["work-item", workItemId], queryFn: () => workItemApi.get(workItemId), enabled: Boolean(workItemId) });
  const item = itemQuery.data;
  const batchQuery = useQuery({
    queryKey: ["batch", item?.batchId],
    queryFn: () => batchApi.get(item!.batchId),
    enabled: Boolean(item?.batchId),
  });
  const formQuery = useQuery({
    queryKey: ["form", item?.templateCode, item?.templateVersion],
    queryFn: () => templateApi.formVersion(item!.templateCode, item!.templateVersion!),
    enabled: Boolean(item?.templateCode && item?.templateVersion),
  });
  const previousQuery = useQuery({ queryKey: ["work-item", workItemId, "previous"], queryFn: () => workItemApi.previous(workItemId), enabled: Boolean(item) });
  const nextQuery = useQuery({ queryKey: ["work-item", workItemId, "next"], queryFn: () => workItemApi.next(workItemId), enabled: Boolean(item) });

  useEffect(() => { if (item) reset(item.data ?? {}); }, [item, reset]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (isDirty) event.preventDefault(); };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isDirty]);

  const isAssignedToCurrentUser = Boolean(item && (!securityEnabled || item.assignedUserId === user?.id));
  const assignedToAnotherUser = Boolean(item?.assignedUserId && !isAssignedToCurrentUser);
  const editable = Boolean(item && isAssignedToCurrentUser && ["UNPROCESSED", "DRAFT", "REJECTED"].includes(item.status) && hasPermission(Permissions.RECORD_UPDATE));
  const canSubmit = editable && (hasPermission(Permissions.RECORD_SUBMIT) || hasPermission(Permissions.RECORD_COMPLETE));
  const canApprove = item?.status === "PENDING_APPROVAL" && hasPermission(Permissions.RECORD_APPROVE);
  const canReject = item?.status === "PENDING_APPROVAL" && hasPermission(Permissions.RECORD_REJECT);
  const invalidate = (updatedId = workItemId) => {
    void queryClient.invalidateQueries({ queryKey: ["work-item", updatedId] });
    void queryClient.invalidateQueries({ queryKey: ["work-items"] });
    void queryClient.invalidateQueries({ queryKey: ["batch"] });
    void queryClient.invalidateQueries({ queryKey: ["batches"] });
  };
  const saveMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) => workItemApi.saveDraft(workItemId, { data: values, rowVersion: item?.rowVersion, dynamicRowVersion: item?.dynamicRowVersion }),
    onSuccess: (updated) => { reset(updated.data ?? getValues()); queryClient.setQueryData(["work-item", workItemId], updated); invalidate(); message.success("Đã lưu bản nháp"); },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const submitMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) => workItemApi.submit(workItemId, { data: values, rowVersion: item?.rowVersion, dynamicRowVersion: item?.dynamicRowVersion }),
    onSuccess: async (updated) => {
      reset(updated.data ?? getValues());
      queryClient.setQueryData(["work-item", workItemId], updated);
      invalidate();
      message.success("Đã gửi hồ sơ chờ duyệt");
      try {
        const next = await workItemApi.next(workItemId);
        if (next) navigate(`/work-items/${next.id}`, { replace: true });
      } catch { /* Submission succeeded; staying on the current item is safe. */ }
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });
  const approveMutation = useMutation({ mutationFn: () => workItemApi.approve(workItemId, item?.rowVersion), onSuccess: async () => { message.success("Đã phê duyệt hồ sơ"); invalidate(); const next = await workItemApi.next(workItemId).catch(() => null); if (next) navigate(`/work-items/${next.id}`, { replace: true }); else void itemQuery.refetch(); }, onError: (error) => message.error(getErrorMessage(error)) });
  const rejectMutation = useMutation({ mutationFn: () => workItemApi.reject(workItemId, rejectReason.trim(), item?.rowVersion), onSuccess: async () => { message.success("Đã trả hồ sơ cho người nhập liệu"); setRejectOpen(false); setRejectReason(""); invalidate(); const next = await workItemApi.next(workItemId).catch(() => null); if (next) navigate(`/work-items/${next.id}`, { replace: true }); else void itemQuery.refetch(); }, onError: (error) => message.error(getErrorMessage(error)) });
  const reopenMutation = useMutation({ mutationFn: () => workItemApi.reopen(workItemId, reopenReason.trim(), item?.rowVersion), onSuccess: () => { message.success("Đã thu hồi phê duyệt để chỉnh sửa"); setReopenOpen(false); setReopenReason(""); invalidate(); void itemQuery.refetch(); }, onError: (error) => message.error(getErrorMessage(error)) });

  const metadata = formQuery.data;
  const validateBusinessRules = (values: Record<string, unknown>) => {
    const invalidRule = (metadata?.rules ?? []).find((rule) => rule.ruleType === "AT_LEAST_ONE_FILLED"
      && !rule.fieldCodes.some((code) => values[code] !== undefined && values[code] !== null && String(values[code]).trim() !== ""));
    if (!invalidRule) return true;
    message.error(invalidRule.errorMessage ?? "Cần nhập ít nhất một trong các trường liên quan.");
    const first = invalidRule.fieldCodes[0];
    if (first) { document.getElementById(first)?.scrollIntoView({ behavior: "smooth", block: "center" }); setFocus(first); }
    return false;
  };
  const submit = handleSubmit((values) => { if (validateBusinessRules(values)) submitMutation.mutate(values); }, (errors) => {
    const first = Object.keys(errors)[0];
    const field = metadata?.groups.flatMap((group) => group.fields ?? []).find((definition) => definition.fieldCode === first);
    message.error(first ? `Vui lòng kiểm tra trường “${field?.label ?? first}”` : "Vui lòng kiểm tra dữ liệu đã nhập");
    if (first) { document.getElementById(first)?.scrollIntoView({ behavior: "smooth", block: "center" }); setFocus(first); }
  });
  const navigateSafely = (id?: string) => {
    if (!id) return;
    if (isDirty && !window.confirm("Bạn có thay đổi chưa lưu. Vẫn chuyển sang hồ sơ khác?")) return;
    navigate(`/work-items/${id}`);
  };
  const position = useMemo(() => item
    ? `${item.sequenceNo} / ${batchQuery.data?.counters?.total ?? "—"}`
    : "—", [item, batchQuery.data?.counters?.total]);

  if (itemQuery.isLoading || formQuery.isLoading) return <div className="center-spin"><Spin size="large" /></div>;
  if (itemQuery.isError || !item) return <PageError message={getErrorMessage(itemQuery.error)} onRetry={() => void itemQuery.refetch()} />;
  if (formQuery.isError || !metadata) return <PageError message={getErrorMessage(formQuery.error)} onRetry={() => void formQuery.refetch()} />;

  return <div className="entry-page batch-entry-page">
    <div className="entry-topbar batch-entry-topbar">
      <div className="entry-title">
        <Tooltip title="Về đợt hồ sơ"><Button type="text" aria-label="Về đợt hồ sơ" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/workspaces/${item.templateCode}/batches/${item.batchId}`)} /></Tooltip>
        <div><h1>{item.documentName}</h1><small>{item.batchName} · Hồ sơ {position}</small></div>
        <WorkflowStatusTag status={item.status} />
      </div>
      <Space className="batch-entry-actions" size={6}>
        <Button type="text" icon={<LeftOutlined />} disabled={!previousQuery.data} aria-label="Hồ sơ trước" onClick={() => navigateSafely(previousQuery.data?.id)} />
        <span className="compare-position">{position}</span>
        <Button type="text" icon={<RightOutlined />} disabled={!nextQuery.data} aria-label="Hồ sơ tiếp theo" onClick={() => navigateSafely(nextQuery.data?.id)} />
        {editable && <Button icon={<SaveOutlined />} loading={saveMutation.isPending} onClick={() => saveMutation.mutate(getValues())}>Lưu nháp</Button>}
        {canSubmit && <Button type="primary" icon={<SendOutlined />} loading={submitMutation.isPending} onClick={() => void submit()}>Gửi duyệt</Button>}
        {canReject && <Button danger icon={<CloseOutlined />} onClick={() => setRejectOpen(true)}>Trả lại</Button>}
        {canApprove && <Button type="primary" icon={<CheckOutlined />} loading={approveMutation.isPending} onClick={() => approveMutation.mutate()}>Phê duyệt</Button>}
        {item.status === "APPROVED" && hasPermission(Permissions.RECORD_REOPEN) && <Button icon={<UndoOutlined />} onClick={() => setReopenOpen(true)}>Thu hồi duyệt</Button>}
      </Space>
    </div>
    <div className="entry-workspace">
      <DocumentViewer initialUrl={item.documentUrl || (item.documentId ? documentApi.contentUrl(item.documentId) : undefined)} readOnly />
      <main className="form-pane">
        {assignedToAnotherUser && <Alert className="workflow-alert" type="info" showIcon message="Chế độ chỉ theo dõi" description={`Hồ sơ đang được giao cho ${item.assignedDisplayName || item.assignedUsername || "người nhập liệu khác"}. Dữ liệu chỉ được chỉnh sửa bởi nhân viên nhập liệu đang phụ trách.`} />}
        {item.status === "REJECTED" && <Alert className="rejection-alert" type="error" showIcon message="Hồ sơ cần chỉnh sửa" description={item.rejectionReason || "Người duyệt chưa ghi lý do."} />}
        {item.status === "PENDING_APPROVAL" && <Alert className="workflow-alert" type="warning" showIcon message="Hồ sơ đang chờ duyệt" description="Dữ liệu được khóa với người nhập liệu trong thời gian kiểm tra." />}
        {item.status === "APPROVED" && <Alert className="workflow-alert" type="success" showIcon message="Hồ sơ đã được phê duyệt" description={item.approvedBy ? `Người duyệt: ${item.approvedBy}` : "Dữ liệu đang ở chế độ chỉ đọc."} />}
        <DynamicFormRenderer metadata={metadata} control={control} disabled={!editable} />
        <div className="mobile-entry-actions batch-mobile-actions">
          <Button icon={<LeftOutlined />} disabled={!previousQuery.data} onClick={() => navigateSafely(previousQuery.data?.id)}>Trước</Button>
          {editable && <Button icon={<SaveOutlined />} loading={saveMutation.isPending} onClick={() => saveMutation.mutate(getValues())}>Lưu nháp</Button>}
          {canSubmit && <Button type="primary" icon={<SendOutlined />} loading={submitMutation.isPending} onClick={() => void submit()}>Gửi duyệt</Button>}
          {canApprove && <Button type="primary" icon={<CheckOutlined />} loading={approveMutation.isPending} onClick={() => approveMutation.mutate()}>Duyệt</Button>}
          <Button icon={<RightOutlined />} disabled={!nextQuery.data} onClick={() => navigateSafely(nextQuery.data?.id)}>Sau</Button>
        </div>
        {editable && isDirty && <div className="unsaved-indicator">Có thay đổi chưa lưu</div>}
      </main>
    </div>

    <Modal title="Trả lại hồ sơ" open={rejectOpen} onCancel={() => setRejectOpen(false)} onOk={() => rejectMutation.mutate()} okText="Trả lại cho người nhập" okButtonProps={{ danger: true, disabled: rejectReason.trim().length < 5 }} confirmLoading={rejectMutation.isPending}>
      <p>Ghi rõ nội dung cần sửa để người nhập liệu xử lý đúng ngay lần tiếp theo.</p>
      <textarea className="workflow-reason-input" rows={4} value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} placeholder="Ví dụ: Ngày cấp và diện tích chưa khớp tài liệu nguồn…" />
    </Modal>
    <Modal title="Thu hồi phê duyệt" open={reopenOpen} onCancel={() => setReopenOpen(false)} onOk={() => reopenMutation.mutate()} okText="Thu hồi để chỉnh sửa" okButtonProps={{ disabled: reopenReason.trim().length < 5 }} confirmLoading={reopenMutation.isPending}>
      <p>Thao tác này mở khóa hồ sơ đã duyệt và được ghi lại trong lịch sử audit.</p>
      <textarea className="workflow-reason-input" rows={4} value={reopenReason} onChange={(event) => setReopenReason(event.target.value)} placeholder="Nhập lý do cần chỉnh sửa lại hồ sơ…" />
    </Modal>
  </div>;
}
