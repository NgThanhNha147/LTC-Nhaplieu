# Kiến trúc hệ thống

## 1. Mục tiêu

Hệ thống cho phép định nghĩa biểu mẫu bằng metadata, tạo bảng PostgreSQL theo phiên bản template và vận hành dữ liệu qua một bộ API/UI dùng chung. Thiết kế POC ưu tiên tính đúng đắn, dễ triển khai và có đường mở rộng rõ ràng.

## 2. Lựa chọn kiến trúc

Backend là modular monolith thay vì microservices. Các module template, schema generation và dynamic data cần dùng chung transaction và metadata; tách service sớm sẽ làm tăng chi phí vận hành mà chưa tạo giá trị cho POC.

```text
Browser
  |
  +-- Template Designer ----+
  +-- Dynamic Form ---------+--> REST API
  +-- Search / Export ------+        |
                                    v
                         Spring Boot application
                         +-----------------------+
                         | template / field      |
                         | schema generator      |
                         | dynamic data / query  |
                         | lookup / document     |
                         | export / audit        |
                         +-----------------------+
                                    |
                                    v
                              PostgreSQL 16
```

## 3. Phân vùng dữ liệu

| Schema | Trách nhiệm |
|---|---|
| `app_meta` | Template, version, group, field, validation, lookup registry, document metadata |
| `app_data` | Các bảng record được generate từ template version |
| `ref_data` | Danh mục do hệ thống quản lý hoặc được phê duyệt |
| `app_audit` | Lịch sử thay đổi dữ liệu và thao tác quản trị |

File PDF/ảnh được lưu ngoài database. Database chỉ giữ storage key, metadata, checksum và liên kết record.

## 4. Vòng đời template

```text
DRAFT -> VALIDATED -> GENERATED -> PUBLISHED -> ARCHIVED
```

- `DRAFT`: thay đổi cấu trúc tự do.
- `VALIDATED`: metadata vượt qua kiểm tra logic.
- `GENERATED`: bảng, constraint và index đã được tạo.
- `PUBLISHED`: cho phép nhập dữ liệu.
- `ARCHIVED`: ngừng tạo record mới nhưng vẫn đọc/export dữ liệu cũ.

Khi publish, phần metadata ảnh hưởng schema được xem là bất biến. Thay đổi data type, độ dài, nullable, unique hoặc lookup value type phải tạo version mới. Label, help text, thứ tự và layout có thể cập nhật nếu không làm thay đổi contract dữ liệu.

## 5. Version và bảng vật lý

Tên bảng được backend sinh theo quy tắc:

```text
app_data.dyn_<normalized_template_code>_v<version>
```

Ví dụ:

```text
app_data.dyn_land_certificate_v1
app_data.dyn_land_certificate_v2
```

Mỗi bảng luôn có các cột hệ thống:

- `id UUID PRIMARY KEY`
- `source_document_id UUID`
- `record_status VARCHAR(30)`
- `created_at`, `created_by`, `updated_at`, `updated_by`
- `row_version BIGINT` cho optimistic locking
- `deleted BOOLEAN` cho soft delete

Metadata lưu `physical_schema`, `physical_table` và `config_hash`. Trước mỗi thao tác dữ liệu, backend xác định bảng/cột từ metadata phía server, không tin identifier do client gửi.

## 6. Generate DDL an toàn

Quy trình generate:

```text
Lock template version
  -> kiểm tra trạng thái
  -> validate metadata
  -> chuẩn hóa identifier
  -> map enum type sang SQL type
  -> so sánh với version đang publish
  -> chặn xóa field, đổi cột/kiểu/độ dài và required thiếu default
  -> dựng DDL
  -> CREATE TABLE
  -> copy cột hệ thống và field tương thích từ bảng cũ
  -> tạo INDEX/CONSTRAINT
  -> lưu config hash và trạng thái
  -> commit
```

Version mới được preview qua `GET /api/v1/template-versions/{id}/migration-plan`. Field mới optional nhận `NULL`; field mới có default được backfill bằng default. Ngay trước publish, backend khóa ghi ngắn trên bảng nguồn và đồng bộ lại dữ liệu mới phát sinh sau thời điểm generate. Khi publish thành công, version đang chạy trước đó chuyển sang `ARCHIVED` và vẫn giữ nguyên bảng vật lý làm snapshot.

Các giới hạn bắt buộc:

- Identifier chỉ khớp `^[a-z][a-z0-9_]{0,62}$` và không nằm trong danh sách từ khóa cấm.
- SQL type chỉ đến từ enum nội bộ, ví dụ `STRING -> VARCHAR(n)`.
- `length`, `precision`, `scale` phải là số trong ngưỡng hệ thống quy định.
- Tên bảng/cột được quote bằng API phù hợp sau khi validate, không nối input tùy ý.
- Value trong CRUD/search luôn dùng bind parameter.
- Generate dùng row lock hoặc PostgreSQL advisory lock để chống chạy trùng.
- PostgreSQL transactional DDL đảm bảo lỗi ở index/constraint sẽ rollback cả lần generate.

## 7. Dynamic CRUD

Payload dùng `fieldCode`, không dùng tên cột vật lý. Backend xử lý theo chuỗi bước:

```text
Resolve published version
 -> load/cache metadata
 -> reject unknown fields
 -> convert values by data type
 -> validate required/rules/lookup/unique
 -> map fieldCode to trusted columnName
 -> execute parameterized SQL
 -> write audit log
```

JPA dùng cho bảng metadata có cấu trúc cố định. `NamedParameterJdbcTemplate` hoặc JDBC abstraction tương đương dùng cho bảng động vì không thể tạo JPA entity tại compile time.

## 8. Search

API chỉ nhận bộ lọc có cấu trúc. Mỗi data type có tập operator được phép:

| Data type | Operator |
|---|---|
| String | `EQ`, `NE`, `CONTAINS`, `STARTS_WITH`, `IN` |
| Number | `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN` |
| Date/time | `EQ`, `BEFORE`, `AFTER`, `BETWEEN` |
| Boolean | `EQ` |
| List | `EQ`, `IN` |
| Chung | `IS_NULL`, `IS_NOT_NULL` |

Backend kiểm tra field tồn tại, `searchable=true`, operator tương thích, sort field hợp lệ và page size trong giới hạn trước khi tạo câu query.

## 9. Lookup

Lookup source được đăng ký trong metadata bởi quản trị viên. Designer chọn lookup bằng code; không được nhập câu SQL.

```text
LAND_TYPE
  schema       = ref_data
  table        = land_type
  value_column = code
  label_column = name
  active_column= active
```

Record lưu `value`, UI hiển thị `label`. Danh mục lớn dùng autocomplete server-side, debounce và pagination. Có thể tạo foreign key khi nguồn nằm cùng database, ổn định và không hard delete; trường hợp khác validate tại application layer.

## 10. Validation

Frontend sinh rule từ metadata để phản hồi nhanh. Backend thực thi lại toàn bộ rule và là nguồn xác thực cuối cùng.

Backend phải kiểm tra:

- Field lạ, field thiếu và type conversion.
- Required theo hành động `SAVE_DRAFT` hoặc `COMPLETE`.
- Min/max length, min/max value, regex, date range và decimal scale.
- Lookup value tồn tại/active.
- Unique constraint và xung đột phiên bản record.

Database constraint bảo vệ các invariant phù hợp; application validation cung cấp thông báo lỗi theo field dễ hiểu.

## 11. Transaction và audit

Một transaction tạo/sửa record gồm validate, ghi bảng động và ghi audit. Audit lưu snapshot JSONB trước/sau, user, action, request ID và thời điểm.

Update sử dụng optimistic locking:

```sql
UPDATE app_data.dyn_example_v1
SET owner_name = :ownerName,
    row_version = row_version + 1
WHERE id = :id
  AND row_version = :expectedVersion
  AND deleted = false;
```

Không cập nhật được dòng nào thì trả `409 Conflict`.

## 12. Cache và hiệu năng

Metadata published được đọc nhiều và thay đổi ít, nên cache theo `templateCode + version`. Cache phải bị vô hiệu hóa khi publish hoặc cập nhật metadata chỉ ảnh hưởng UI.

Chỉ tạo index cho field unique, foreign key, searchable/sortable thường dùng. Không index toàn bộ field. Export lớn nên chuyển thành background job ở phase sau; POC giới hạn số dòng export đồng bộ.

## 13. Khả năng mở rộng sau POC

- Repeatable group bằng bảng con có khóa tới record cha.
- Migration planner và data mapping giữa các template version.
- Rule liên trường và computed fields.
- Workflow duyệt dữ liệu.
- Object storage S3/MinIO và virus scanning.
- Background export/import queue.
- Field-level permission và masking dữ liệu nhạy cảm.
- Tách document, export hoặc search thành service khi tải vận hành thực sự yêu cầu.

