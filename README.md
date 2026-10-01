# Dynamic Form Platform

Nền tảng tạo biểu mẫu nhập liệu động dựa trên metadata. Người quản trị cấu hình template, nhóm thông tin, field, validation và nguồn danh mục; hệ thống tạo bảng PostgreSQL tương ứng, render form React và cung cấp một bộ API CRUD/tìm kiếm/export dùng chung.

Hai màn hình hồ sơ đất đai trong yêu cầu chỉ là dữ liệu demo. Kiến trúc không hard-code theo một loại hồ sơ cụ thể.

## Kiến trúc POC

```text
React + TypeScript
  - Template Designer
  - Dynamic Form Renderer
  - Document Viewer
  - Record Search / Export
             |
             v
Spring Boot modular monolith
  - Template & Field Metadata
  - Schema Generator
  - Dynamic CRUD / Search
  - Lookup / Export / Audit
             |
             v
PostgreSQL
  - app_meta  : metadata và cấu hình
  - app_data  : bảng dữ liệu được generate
  - ref_data  : dữ liệu danh mục
  - app_audit : lịch sử thay đổi
```

Nguyên tắc chính:

- React render form tại runtime từ metadata, không sinh source code riêng cho từng template.
- Spring Boot dùng API động dùng chung, không sinh controller/service mới cho từng template.
- JPA phù hợp với metadata cố định; JDBC phù hợp với DDL và bảng dữ liệu động.
- Tên bảng/cột do backend chuẩn hóa và kiểm tra theo allowlist; client không được gửi SQL.
- Mỗi phiên bản template đã publish gắn với một bảng vật lý riêng.
- Thay đổi schema sau publish tạo version mới; thay đổi label/layout có thể chỉ cập nhật metadata.

Chi tiết quyết định kiến trúc nằm tại [docs/architecture.md](docs/architecture.md). Ví dụ request/response nằm tại [docs/api-examples.md](docs/api-examples.md).

## Cấu trúc repository

```text
.
├── backend/                 # Spring Boot
├── frontend/                # React + TypeScript
├── docs/
│   ├── architecture.md
│   └── api-examples.md
├── scripts/
│   ├── check-environment.ps1
│   └── smoke-test.ps1
├── .env.example
├── docker-compose.yml
└── README.md
```

## Yêu cầu môi trường

Chạy hoàn toàn bằng Docker:

- Docker Desktop hoặc Docker Engine có Compose v2.

Chạy chế độ phát triển:

- Java 21.
- Maven 3.9+ hoặc Maven Wrapper trong `backend/`.
- Node.js 20+ và npm/pnpm theo lockfile của `frontend/`.
- PostgreSQL 16, hoặc chỉ chạy database bằng Docker.

Kiểm tra nhanh công cụ trên Windows:

```powershell
./scripts/check-environment.ps1
```

## Cấu hình môi trường

Tạo file `.env` từ mẫu:

```powershell
Copy-Item .env.example .env
```

Đổi `POSTGRES_PASSWORD`, `JWT_SECRET` và `INITIAL_ADMIN_PASSWORD` trước khi khởi động môi trường mới. Bảo mật được bật mặc định; tài khoản quản trị khởi tạo buộc phải đổi mật khẩu sau lần đăng nhập đầu tiên. File `.env` đã được bỏ qua bởi Git.

Các cổng mặc định:

| Thành phần | URL/cổng |
|---|---|
| Frontend | `http://localhost:5173` |
| Backend | `http://localhost:8080` |
| API | `http://localhost:8080/api/v1` |
| PostgreSQL | `localhost:5432` |

## Chạy chế độ phát triển

### 1. Chạy PostgreSQL bằng Docker

```powershell
docker compose up -d postgres
docker compose ps
```

Backend chạy ngoài Docker cần kết nối:

```text
jdbc:postgresql://localhost:5432/dynamic_form
username: dynamic_form
password: giá trị POSTGRES_PASSWORD trong .env
```

Thiết lập biến môi trường cho terminal chạy backend (đổi password nếu đã sửa `.env`):

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/dynamic_form"
$env:DB_USERNAME = "dynamic_form"
$env:DB_PASSWORD = "change_me_in_real_environments"
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
$env:DOCUMENT_STORAGE_PATH = "./data/documents"
```

### 2. Chạy backend

Nếu dự án có Maven Wrapper:

```powershell
Set-Location backend
./mvnw.cmd spring-boot:run
```

Nếu dùng Maven cài trên máy:

```powershell
Set-Location backend
mvn spring-boot:run
```

Flyway sẽ tạo/cập nhật schema metadata khi ứng dụng khởi động.

### 3. Chạy frontend

Mở terminal khác:

```powershell
Set-Location frontend
npm install
npm run dev
```

Frontend đọc `VITE_API_BASE_URL`; mặc định nên là `http://localhost:8080/api/v1`.

## Chạy toàn bộ bằng Docker

Các module `backend/` và `frontend/` cần có `Dockerfile` tương ứng.

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

Theo dõi log:

```powershell
docker compose logs -f backend frontend
```

Sau khi backend đã healthy, chạy smoke test tạo template, generate/publish bảng, CRUD, search và export:

```powershell
./scripts/smoke-test.ps1
```

Dừng hệ thống nhưng giữ dữ liệu:

```powershell
docker compose down
```

Chỉ xóa volume khi chắc chắn không cần dữ liệu local:

```powershell
docker compose down --volumes
```

## Luồng demo đề xuất

1. Tạo template `LAND_CERTIFICATE` ở trạng thái `DRAFT`.
2. Tạo các group như “Thông tin phát hành”, “Chủ sử dụng đất”, “Thửa đất chính”.
3. Tạo field thuộc nhiều kiểu: chuỗi, số thập phân, ngày, boolean, textarea và lookup.
4. Đăng ký lookup `LAND_TYPE` từ một bảng thuộc schema `ref_data`.
5. Xem trước form và chạy validate cấu hình.
6. Xem trước DDL, sau đó generate bảng PostgreSQL.
7. Publish template và mở màn hình nhập liệu hai cột tài liệu/form.
8. Tạo, sửa và xem chi tiết một record.
9. Tìm theo tên chủ sử dụng, khoảng diện tích và loại đất.
10. Export kết quả lọc ra XLSX hoặc CSV.
11. Tạo thêm template khác để chứng minh renderer/API không phụ thuộc hồ sơ đất đai.

## API tổng quan

| Nhóm | Endpoint chính |
|---|---|
| Template | `/api/v1/templates` |
| Version | `/api/v1/templates/{id}/versions` |
| Group | `/api/v1/template-versions/{id}/groups` |
| Field | `/api/v1/template-versions/{id}/fields` |
| Validate/migration | `/api/v1/template-versions/{id}/validate`, `/migration-plan`, `/generate`, `/publish` |
| Form metadata | `/api/v1/forms/{templateCode}` |
| Dynamic records | `/api/v1/templates/{templateCode}/records` |
| Search | `/api/v1/templates/{templateCode}/records/search` |
| Export | `/api/v1/templates/{templateCode}/records/export` |
| Lookup | `/api/v1/lookups/{lookupCode}/options` |
| Documents | `/api/v1/documents` |

Nếu backend bật OpenAPI, tài liệu tương tác dự kiến tại `http://localhost:8080/swagger-ui.html`.

## Phạm vi POC

Bao gồm:

- CRUD template, version, group, field và validation rule.
- Field cơ bản: string, textarea, integer, decimal, date, datetime, boolean và list.
- Lookup database thông qua registry/allowlist.
- Validate metadata, preview DDL, generate table và publish.
- Preview migration, chặn thay đổi phá vỡ dữ liệu và tự copy record tương thích sang version mới.
- Dynamic form, CRUD, search/filter/sort/page và export.
- Upload/preview tài liệu cơ bản, audit log và optimistic locking.

Chưa ưu tiên trong POC:

- Drag-and-drop designer nâng cao.
- Repeatable group/bảng con.
- Workflow phê duyệt nhiều bước.
- Công thức tính toán và rule liên trường phức tạp.
- Chuyển đổi kiểu dữ liệu, đổi tên cột hoặc xóa field tự động giữa các version.
- Remote lookup API, Elasticsearch hoặc microservices.

## Quy ước an toàn

- Không nhận raw SQL, tên schema, tên bảng hoặc tên cột từ API dữ liệu.
- Chỉ hỗ trợ data type và toán tử tìm kiếm trong allowlist.
- Bind mọi giá trị tìm kiếm bằng parameter.
- Generate schema trong transaction và khóa version để chống chạy đồng thời.
- Không xóa vật lý cột dữ liệu khi field bị bỏ; đánh dấu deprecated trước.
- Không lưu file lớn trực tiếp trong PostgreSQL; POC dùng volume, production dùng S3/MinIO.
- Export phải có quyền riêng, giới hạn dòng và audit.

## Kiểm thử tối thiểu trước demo

- Unit test mapping metadata sang PostgreSQL type và rule validation.
- Integration test bằng PostgreSQL/Testcontainers cho generate DDL và dynamic CRUD.
- Test SQL injection qua field code, filter, sort và lookup keyword.
- Test optimistic locking trả `409 Conflict`.
- Test một template thứ hai chạy cùng renderer và API.
- Smoke test Docker Compose từ database rỗng.

