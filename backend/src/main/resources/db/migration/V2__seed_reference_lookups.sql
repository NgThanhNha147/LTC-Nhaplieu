CREATE TABLE ref_data.document_type (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE ref_data.land_type (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0
);

INSERT INTO ref_data.document_type (code, name, display_order) VALUES
    ('CCCD', 'Căn cước công dân', 1),
    ('CMND', 'Chứng minh nhân dân', 2),
    ('PASSPORT', 'Hộ chiếu', 3),
    ('OTHER', 'Giấy tờ khác', 4);

INSERT INTO ref_data.land_type (code, name, display_order) VALUES
    ('ODT', 'Đất ở tại đô thị', 1),
    ('ONT', 'Đất ở tại nông thôn', 2),
    ('LUC', 'Đất chuyên trồng lúa nước', 3),
    ('CLN', 'Đất trồng cây lâu năm', 4),
    ('BHK', 'Đất bằng trồng cây hàng năm khác', 5),
    ('TMD', 'Đất thương mại, dịch vụ', 6),
    ('SKC', 'Đất cơ sở sản xuất phi nông nghiệp', 7),
    ('OTHER', 'Loại đất khác', 99);

INSERT INTO app_meta.lookup_source (
    id, code, name, source_type, source_schema, source_table,
    value_column, label_column, active_column, parent_column,
    sort_column, status, created_at
) VALUES
    (
        '11111111-1111-1111-1111-111111111101',
        'DOCUMENT_TYPE',
        'Loại giấy tờ tùy thân',
        'TABLE',
        'ref_data',
        'document_type',
        'code',
        'name',
        'active',
        NULL,
        'display_order',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        '11111111-1111-1111-1111-111111111102',
        'LAND_TYPE',
        'Loại đất',
        'TABLE',
        'ref_data',
        'land_type',
        'code',
        'name',
        'active',
        NULL,
        'display_order',
        'ACTIVE',
        CURRENT_TIMESTAMP
    );
