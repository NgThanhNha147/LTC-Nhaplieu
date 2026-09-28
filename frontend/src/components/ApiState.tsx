import { Alert, Button, Empty, Result, Skeleton } from "antd";

export function PageLoading() {
  return <div className="surface-card"><Skeleton active paragraph={{ rows: 8 }} /></div>;
}

export function PageError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const normalizedMessage = message.toLocaleLowerCase("vi");
  const unpublished = normalizedMessage.includes("chưa được đưa vào sử dụng")
    || normalizedMessage.includes("chưa sẵn sàng nhập dữ liệu");
  if (unpublished) {
    return (
      <Result
        status="warning"
        title="Biểu mẫu chưa sẵn sàng nhập dữ liệu"
        subTitle={<span>Biểu mẫu này chưa được đưa vào sử dụng. Hãy vào <strong>Cấu hình bảng</strong>, thực hiện lần lượt: kiểm tra cấu hình → tạo bảng dữ liệu → đưa vào sử dụng.</span>}
        extra={[
          <Button key="back" type="primary" onClick={() => window.history.back()}>Quay lại cấu hình</Button>,
          onRetry && <Button key="retry" onClick={onRetry}>Kiểm tra lại</Button>,
        ].filter(Boolean)}
      />
    );
  }
  return <Result status="error" title="Không thể tải dữ liệu" subTitle={message} extra={onRetry && <Button onClick={onRetry}>Thử lại</Button>} />;
}

export function EmptyPanel({ title, description, action }: { title: string; description?: string; action?: React.ReactNode }) {
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<><strong>{title}</strong>{description && <div className="empty-hint">{description}</div>}</>} >{action}</Empty>;
}

export function OfflineHint() {
  return <Alert showIcon type="info" message="Chưa có dữ liệu" description="Hãy tạo dữ liệu mới hoặc thử tải lại trang." />;
}
