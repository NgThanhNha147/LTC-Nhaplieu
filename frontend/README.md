# FormFoundry Frontend

React + TypeScript frontend cho POC metadata-driven Dynamic Form System.

## Chạy local

Yêu cầu Node.js 22+.

```bash
npm install
copy .env.example .env
npm run dev
```

Mặc định ứng dụng gọi backend tại `http://localhost:8080/api/v1`. Có thể đổi bằng:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## Build production

```bash
npm run build
npm run preview
```

Hoặc Docker:

```bash
docker build --build-arg VITE_API_BASE_URL=http://localhost:8080/api/v1 -t formfoundry-ui .
docker run --rm -p 3000:80 formfoundry-ui
```

Lưu ý: biến `VITE_API_BASE_URL` được nhúng ở build time. Khi chạy trong trình duyệt, URL phải là địa chỉ mà trình duyệt truy cập được, không phải hostname nội bộ của container backend.

## Màn hình

- `/templates`: danh sách, tạo và chỉnh sửa template.
- `/templates/:templateId/designer`: quản lý version, group, field, validation, lookup; validate, preview DDL, generate và publish.
- `/workspaces/:templateCode`: danh sách record, filter, phân trang, xóa và export.
- `/workspaces/:templateCode/new`: form nhập liệu sinh từ metadata.
- `/workspaces/:templateCode/records/:recordId`: xem và sửa record.

## API adapter

Toàn bộ giao tiếp backend nằm trong `src/api`. Adapter chấp nhận cả response trực tiếp và envelope `{ "data": ... }`; page adapter chấp nhận cả `items` và Spring Page `content`. Nếu contract backend thay đổi, ưu tiên sửa adapter thay vì sửa component.

Các endpoint chính:

- `GET/POST /templates`
- `GET/POST /templates/{id}/versions`
- `GET/POST /template-versions/{id}/groups`
- `GET/POST /template-versions/{id}/fields`
- `POST /template-versions/{id}/validate|generate|publish`
- `GET /template-versions/{id}/migration-plan`
- `GET /template-versions/{id}/ddl-preview`
- `GET /forms/{templateCode}`
- `POST /templates/{templateCode}/records/search`
- `POST /templates/{templateCode}/records/export`
- `GET /lookups/{lookupCode}/options`

## Phạm vi POC

- Upload trong document viewer hiện tạo local object URL để preview. Việc upload thật được giữ ở backend/document service.
- Lookup source list dùng `GET /lookup-sources`; nếu backend chưa có endpoint này, designer vẫn hoạt động và hiển thị danh sách trống.
- Authentication chưa được gắn vào UI. Có thể bổ sung bearer-token interceptor tại `src/api/client.ts`.
