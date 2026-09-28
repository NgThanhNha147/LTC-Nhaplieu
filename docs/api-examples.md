# Ví dụ API

Các ví dụ dùng base URL `http://localhost:8080/api/v1`. UUID chỉ có tính minh họa.

## 1. Tạo template

```http
POST /api/v1/templates
Content-Type: application/json
```

```json
{
  "code": "LAND_CERTIFICATE",
  "name": "Giấy chứng nhận quyền sử dụng đất",
  "description": "Template demo nhập liệu hồ sơ đất đai"
}
```

## 2. Tạo group

```http
POST /api/v1/template-versions/8dd25ca4-3739-4ae9-b245-508551795dcc/groups
Content-Type: application/json
```

```json
{
  "code": "LAND_INFO",
  "label": "Thửa đất chính",
  "displayOrder": 30,
  "columnCount": 3,
  "collapsible": true,
  "defaultCollapsed": false
}
```

## 3. Tạo field số thập phân

```http
POST /api/v1/template-versions/8dd25ca4-3739-4ae9-b245-508551795dcc/fields
Content-Type: application/json
```

```json
{
  "groupId": "cdbdd7b6-1e7f-431d-97f8-417aaf2ea6c7",
  "fieldCode": "landArea",
  "label": "Diện tích",
  "dataType": "DECIMAL",
  "componentType": "NUMBER",
  "required": true,
  "precision": 18,
  "scale": 2,
  "searchable": true,
  "sortable": true,
  "exportable": true,
  "gridSpan": 4,
  "validations": [
    {
      "ruleType": "MIN_VALUE",
      "config": { "value": 0 },
      "message": "Diện tích phải lớn hơn hoặc bằng 0"
    }
  ]
}
```

## 4. Validate và generate

```http
POST /api/v1/template-versions/8dd25ca4-3739-4ae9-b245-508551795dcc/validate
```

Response lỗi minh họa:

```json
{
  "valid": false,
  "errors": [
    {
      "fieldCode": "ownerName",
      "code": "MISSING_MAX_LENGTH",
      "message": "Field STRING phải có maxLength"
    }
  ],
  "warnings": []
}
```

Khi metadata hợp lệ:

```http
GET  /api/v1/template-versions/8dd25ca4-3739-4ae9-b245-508551795dcc/migration-plan
GET  /api/v1/template-versions/8dd25ca4-3739-4ae9-b245-508551795dcc/ddl-preview
POST /api/v1/template-versions/8dd25ca4-3739-4ae9-b245-508551795dcc/generate
POST /api/v1/template-versions/8dd25ca4-3739-4ae9-b245-508551795dcc/publish
```

`migration-plan` trả về version/bảng nguồn, số bản ghi cần copy, danh sách field thêm/sửa/xóa và các blocker cần xử lý trước khi generate.

## 5. Lấy metadata render form

```http
GET /api/v1/forms/LAND_CERTIFICATE
```

```json
{
  "templateCode": "LAND_CERTIFICATE",
  "templateName": "Giấy chứng nhận quyền sử dụng đất",
  "version": 1,
  "groups": [
    {
      "code": "LAND_INFO",
      "label": "Thửa đất chính",
      "displayOrder": 30,
      "columnCount": 3,
      "fields": [
        {
          "fieldCode": "landArea",
          "label": "Diện tích",
          "dataType": "DECIMAL",
          "componentType": "NUMBER",
          "required": true,
          "precision": 18,
          "scale": 2,
          "gridSpan": 4
        }
      ]
    }
  ]
}
```

## 6. Tạo record

```http
POST /api/v1/templates/LAND_CERTIFICATE/records
Content-Type: application/json
```

```json
{
  "sourceDocumentId": "622ff7ba-32b6-4f31-a852-037ff710ee02",
  "status": "DRAFT",
  "data": {
    "serialNumber": "CH00667",
    "ownerName": "Nguyễn Văn A",
    "birthDate": "1985-05-20",
    "landArea": 222.50,
    "landType": "RURAL_LAND"
  }
}
```

## 7. Cập nhật có optimistic locking

```http
PUT /api/v1/templates/LAND_CERTIFICATE/records/5d6f030b-0bd7-489e-ac38-ca029350270d
Content-Type: application/json
```

```json
{
  "rowVersion": 3,
  "status": "COMPLETED",
  "data": {
    "serialNumber": "CH00667",
    "ownerName": "Nguyễn Văn B",
    "birthDate": "1985-05-20",
    "landArea": 222.50,
    "landType": "RURAL_LAND"
  }
}
```

Nếu record đã được người khác cập nhật, API trả `409 Conflict`.

## 8. Tìm kiếm động

```http
POST /api/v1/templates/LAND_CERTIFICATE/records/search
Content-Type: application/json
```

```json
{
  "filters": [
    {
      "field": "ownerName",
      "operator": "CONTAINS",
      "value": "Nguyễn"
    },
    {
      "field": "landArea",
      "operator": "GTE",
      "value": 100
    },
    {
      "field": "landType",
      "operator": "IN",
      "value": ["RURAL_LAND", "URBAN_LAND"]
    }
  ],
  "sort": [
    {
      "field": "createdAt",
      "direction": "DESC"
    }
  ],
  "page": 0,
  "size": 20
}
```

## 9. Lookup autocomplete

```http
GET /api/v1/lookups/LAND_TYPE/options?q=nông%20thôn&page=0&size=20
```

```json
{
  "items": [
    {
      "value": "RURAL_LAND",
      "label": "Đất ở tại nông thôn"
    }
  ],
  "page": 0,
  "size": 20,
  "hasMore": false
}
```

## 10. Export theo bộ lọc

```http
POST /api/v1/templates/LAND_CERTIFICATE/records/export
Content-Type: application/json
```

```json
{
  "format": "XLSX",
  "fields": ["serialNumber", "ownerName", "landArea", "landType"],
  "filters": [
    {
      "field": "landArea",
      "operator": "GTE",
      "value": 100
    }
  ],
  "sort": [
    {
      "field": "ownerName",
      "direction": "ASC"
    }
  ]
}
```

## 11. Lỗi validation thống nhất

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "requestId": "01J8QJXC3YZ6QH0F5GQ2A7E5JS",
  "fieldErrors": [
    {
      "field": "landArea",
      "code": "MIN_VALUE",
      "message": "Diện tích phải lớn hơn hoặc bằng 0"
    }
  ]
}
```

Không endpoint nào chấp nhận raw SQL, physical table name hoặc physical column name từ client.

