UPDATE app_meta.sec_function
SET name = CASE code
        WHEN 'BATCH_VIEW' THEN 'Xem đợt hồ sơ'
        WHEN 'BATCH_CREATE' THEN 'Tạo đợt hồ sơ'
        WHEN 'BATCH_ASSIGN' THEN 'Giao đợt hồ sơ'
        WHEN 'BATCH_UPLOAD' THEN 'Tải tài liệu vào đợt'
        WHEN 'BATCH_ARCHIVE' THEN 'Lưu trữ đợt hồ sơ'
        ELSE name
    END,
    function_group = CASE WHEN code IN ('BATCH_VIEW', 'BATCH_CREATE', 'BATCH_ASSIGN', 'BATCH_UPLOAD', 'BATCH_ARCHIVE')
        THEN 'Nhập liệu theo đợt' ELSE function_group END,
    description = CASE code
        WHEN 'BATCH_VIEW' THEN 'Xem đợt hồ sơ và danh sách công việc'
        WHEN 'BATCH_CREATE' THEN 'Tạo đợt hồ sơ và tải tài liệu'
        WHEN 'BATCH_ASSIGN' THEN 'Giao hoặc chuyển người nhập liệu'
        WHEN 'BATCH_UPLOAD' THEN 'Tải PDF, thư mục hoặc ZIP vào đợt hồ sơ'
        WHEN 'BATCH_ARCHIVE' THEN 'Đóng đợt hồ sơ đã hoàn tất'
        ELSE description
    END
WHERE code IN ('BATCH_VIEW', 'BATCH_CREATE', 'BATCH_ASSIGN', 'BATCH_UPLOAD', 'BATCH_ARCHIVE');

UPDATE app_meta.sec_menu
SET label = CASE code
        WHEN 'BATCHES' THEN 'Quản lý đợt'
        WHEN 'MY_BATCHES' THEN 'Đợt của tôi'
        ELSE label
    END
WHERE code IN ('BATCHES', 'MY_BATCHES');
