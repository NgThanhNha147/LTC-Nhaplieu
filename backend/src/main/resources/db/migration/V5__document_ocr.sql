ALTER TABLE app_meta.document_file
    ADD COLUMN ocr_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN ocr_text_path VARCHAR(1000),
    ADD COLUMN ocr_pdf_path VARCHAR(1000),
    ADD COLUMN ocr_engine VARCHAR(40),
    ADD COLUMN ocr_error VARCHAR(2000),
    ADD COLUMN ocr_started_at TIMESTAMPTZ,
    ADD COLUMN ocr_completed_at TIMESTAMPTZ;

CREATE INDEX idx_document_ocr_status ON app_meta.document_file(ocr_status);
