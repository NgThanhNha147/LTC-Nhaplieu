import { CloudServerOutlined, DatabaseOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { Alert, Card, Col, Row, Steps, Tag } from "antd";

export function BackupsPage() {
  return <div className="page-stack backup-guidance">
    <Alert type="warning" showIcon message="Khôi phục dữ liệu là thao tác vận hành có kiểm soát" description="Phiên bản hiện tại chưa cho chạy restore trực tiếp từ trình duyệt. Người có quyền cần thực hiện qua quy trình vận hành đã phê duyệt để tránh ghi đè nhầm dữ liệu đang sử dụng." />
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={8}><Card><div className="backup-card-icon"><DatabaseOutlined /></div><h3>PostgreSQL</h3><p>Sao lưu cấu trúc, metadata, dữ liệu hồ sơ và audit log.</p><Tag color="blue">Theo lịch máy chủ</Tag></Card></Col>
      <Col xs={24} lg={8}><Card><div className="backup-card-icon"><CloudServerOutlined /></div><h3>Tài liệu đính kèm</h3><p>Sao lưu đồng bộ file nguồn với mốc backup cơ sở dữ liệu.</p><Tag color="blue">Kho lưu trữ ngoài máy chủ</Tag></Card></Col>
      <Col xs={24} lg={8}><Card><div className="backup-card-icon"><SafetyCertificateOutlined /></div><h3>Kiểm tra phục hồi</h3><p>Đối chiếu checksum, số bản ghi và chạy smoke test trước khi chuyển đổi.</p><Tag color="gold">Bắt buộc phê duyệt</Tag></Card></Col>
    </Row>
    <Card title="Quy trình phục hồi được phê duyệt"><Steps direction="vertical" current={-1} items={[
      { title: "Xác nhận phạm vi và người phê duyệt", description: "Ghi nhận bản backup, nguyên nhân và thời điểm cần phục hồi." },
      { title: "Đưa hệ thống vào chế độ bảo trì", description: "Ngăn phát sinh dữ liệu mới trong thời gian xử lý." },
      { title: "Backup hiện trạng", description: "Luôn tạo một bản an toàn trước khi thực hiện restore." },
      { title: "Phục hồi vào môi trường tạm", description: "Kiểm tra Flyway, checksum file và số lượng dữ liệu." },
      { title: "Kiểm thử và chuyển đổi", description: "Chỉ đưa vào sử dụng khi toàn bộ kiểm tra đạt yêu cầu." },
    ]} /></Card>
  </div>;
}
