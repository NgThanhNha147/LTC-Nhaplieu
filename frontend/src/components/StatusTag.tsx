import { Tag } from "antd";
import type { TemplateStatus } from "../types";

const colors: Record<TemplateStatus, string> = {
  DRAFT: "default",
  VALIDATED: "blue",
  GENERATED: "gold",
  PUBLISHED: "green",
  ARCHIVED: "red",
};

const labels: Record<TemplateStatus, string> = {
  DRAFT: "Đang cấu hình",
  VALIDATED: "Đã kiểm tra",
  GENERATED: "Đã tạo bảng",
  PUBLISHED: "Đang sử dụng",
  ARCHIVED: "Phiên bản cũ",
};

export function StatusTag({ status }: { status: TemplateStatus }) {
  return <Tag color={colors[status]}>{labels[status] ?? status}</Tag>;
}
