import { CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, EditOutlined, InboxOutlined } from "@ant-design/icons";
import { Tag } from "antd";
import type { WorkItemStatus } from "../types";

const statusMeta: Record<WorkItemStatus, { label: string; color: string; icon: React.ReactNode }> = {
  UNPROCESSED: { label: "Chờ nhập", color: "default", icon: <InboxOutlined /> },
  DRAFT: { label: "Nháp", color: "blue", icon: <EditOutlined /> },
  PENDING_APPROVAL: { label: "Chờ duyệt", color: "gold", icon: <ClockCircleOutlined /> },
  APPROVED: { label: "Đã duyệt", color: "green", icon: <CheckCircleOutlined /> },
  REJECTED: { label: "Bị trả lại", color: "red", icon: <CloseCircleOutlined /> },
};

export function workflowStatusLabel(status?: WorkItemStatus) {
  return status ? statusMeta[status]?.label ?? status : "Không xác định";
}

export function WorkflowStatusTag({ status }: { status: WorkItemStatus }) {
  const meta = statusMeta[status] ?? { label: status, color: "default", icon: null };
  return <Tag className="workflow-status-tag" color={meta.color} icon={meta.icon}>{meta.label}</Tag>;
}
