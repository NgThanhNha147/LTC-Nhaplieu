# Dynamic Form Backend

Spring Boot 3 / Java 21 backend cho POC metadata-driven form. Metadata dùng JPA; DDL và dữ liệu bảng động dùng JDBC có bind parameters và allowlist identifier.

## Chạy local

Yêu cầu PostgreSQL 15+ và Java 21+.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:DB_URL='jdbc:postgresql://localhost:5432/dynamic_forms'
$env:DB_USERNAME='dynamic_forms'
$env:DB_PASSWORD='dynamic_forms'
.\mvnw.cmd spring-boot:run
```

Flyway tự tạo bốn schema: `app_meta`, `app_data`, `ref_data`, `app_audit`.

Build/test:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Docker image có thể build bằng `docker build -t dynamic-form-backend .`.

Mặc định API không yêu cầu đăng nhập để thuận tiện demo. Đặt `APP_SECURITY_ENABLED=true`, `APP_USERNAME` và `APP_PASSWORD` để bật HTTP Basic. CORS mặc định cho `localhost:3000` và `localhost:5173`.

## Luồng POC

1. `POST /api/v1/templates` tạo template và version 1.
2. `POST /api/v1/template-versions/{versionId}/groups` tạo nhóm.
3. `POST /api/v1/template-versions/{versionId}/fields` tạo field.
4. `POST /api/v1/template-versions/{versionId}/validate` kiểm tra metadata.
5. `GET /api/v1/template-versions/{versionId}/ddl-preview` xem DDL.
6. `POST /api/v1/template-versions/{versionId}/generate` tạo bảng `app_data.dyn_<code>_v<n>`.
7. `POST /api/v1/template-versions/{versionId}/publish` phát hành.
8. `GET /api/v1/forms/{templateCode}` lấy metadata render form.
9. Dùng `/api/v1/templates/{templateCode}/records` để nhập và khai thác dữ liệu.

## API chính

| Nhóm | Endpoint |
|---|---|
| Template | `GET/POST /api/v1/templates`, `GET/PUT/DELETE /api/v1/templates/{id}` |
| Version | `POST/GET /api/v1/templates/{id}/versions`, `GET /api/v1/templates/{id}/versions/{no}` |
| Group | `GET/POST /api/v1/template-versions/{id}/groups`, `PUT/DELETE /api/v1/groups/{id}` |
| Field | `GET/POST /api/v1/template-versions/{id}/fields`, `PUT/DELETE /api/v1/fields/{id}` |
| Generate | `POST .../{id}/validate`, `GET .../{id}/ddl-preview`, `POST .../{id}/generate`, `POST .../{id}/publish` |
| Form metadata | `GET /api/v1/forms/{code}`, `GET /api/v1/forms/{code}/versions/{no}` |
| Dynamic data | `POST /api/v1/templates/{code}/records`, `GET/PUT/DELETE .../records/{recordId}` |
| Search | `POST /api/v1/templates/{code}/records/search` |
| Export | `POST /api/v1/templates/{code}/records/export?format=xlsx|csv` |
| Lookup | `GET/POST /api/v1/lookups`, `PUT/DELETE /api/v1/lookups/{id}`, `GET /api/v1/lookups/{code}/options` |
| Document | `POST /api/v1/documents`, `GET /api/v1/documents/{id}/content` |

Ví dụ search:

```json
{
  "keyword": "Nguyen Van A",
  "dateField": "createdAt",
  "fromDate": "2026-09-01",
  "toDate": "2026-09-30",
  "status": "COMPLETED",
  "hasDocument": true,
  "filters": [{"field": "ownerName", "operator": "CONTAINS", "value": "Nguyen"}],
  "sort": [{"field": "createdAt", "direction": "DESC"}],
  "page": 0,
  "size": 20
}
```

`keyword` tìm OR trên các field `searchable` thuộc nhóm text/list. Khoảng ngày dùng múi giờ
`Asia/Ho_Chi_Minh`; `toDate` bao gồm trọn ngày. `dateField` nhận `createdAt` hoặc `updatedAt`.
Backend cũng chấp nhận alias `recordStatus` và `hasAttachment` để tương thích client cũ.

## Quy tắc an toàn

- Tên schema/bảng/cột không lấy trực tiếp từ SQL do client cung cấp; backend chuẩn hóa và kiểm tra allowlist.
- Kiểu SQL chỉ sinh từ enum nội bộ.
- Giá trị CRUD/search luôn bind parameter.
- Lookup POC chỉ đọc bảng trong schema `ref_data` đã đăng ký và kiểm tra tồn tại.
- Version đã generate không được sửa cấu trúc. Version mới được clone từ version gần nhất.
- Delete record là soft delete; update/delete dùng `rowVersion` để tránh ghi đè đồng thời.
- Field `required` được bắt buộc khi record chuyển sang `COMPLETED`, nên vẫn lưu nháp được.

## Giới hạn POC

- Chưa hỗ trợ repeatable group, multi-select, computed field và migration tự động giữa các version.
- Lookup hiện lưu value dạng `VARCHAR(255)` và chưa tự tạo foreign key vật lý.
- Export đồng bộ, giới hạn mặc định 10.000 dòng.
- Local document storage phù hợp demo; production nên thay bằng S3/MinIO và bổ sung antivirus scan.
- Security production cần thay HTTP Basic bằng OAuth2/OIDC và phân quyền theo template.

Lookup table phải được DBA/Flyway tạo trong `ref_data` trước khi đăng ký. Ví dụ:

```sql
CREATE TABLE ref_data.priority (
  code VARCHAR(30) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INTEGER NOT NULL DEFAULT 0
);
```
