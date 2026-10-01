import { Button, Result } from "antd";
import { useNavigate } from "react-router-dom";

export function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <div className="access-result">
      <Result
        status="403"
        title="Bạn chưa được cấp quyền"
        subTitle="Tài khoản hiện tại không được phép truy cập chức năng này. Nếu đây là nhiệm vụ của bạn, hãy liên hệ quản trị viên để được cấp quyền phù hợp."
        extra={<Button type="primary" onClick={() => navigate("/", { replace: true })}>Về trang được cấp quyền</Button>}
      />
    </div>
  );
}
