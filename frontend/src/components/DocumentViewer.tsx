import { FileImageOutlined, FilePdfOutlined, InboxOutlined } from "@ant-design/icons";
import { App, Button, Popconfirm, Upload } from "antd";
import { useEffect, useState } from "react";
import { documentApi } from "../api/documents";

export function DocumentViewer({
  initialUrl,
  onDocumentIdChange,
  readOnly = false,
}: {
  initialUrl?: string;
  onDocumentIdChange?: (id?: string) => void;
  readOnly?: boolean;
}) {
  const { message } = App.useApp();
  const [url, setUrl] = useState(initialUrl ?? "");
  const [uploading, setUploading] = useState(false);

  useEffect(() => setUrl(initialUrl ?? ""), [initialUrl]);

  const isPdf = url.toLowerCase().includes(".pdf")
    || url.startsWith("data:application/pdf")
    || url.startsWith("blob:")
    || url.includes("/documents/");

  const removeDocument = () => {
    setUrl("");
    onDocumentIdChange?.(undefined);
  };

  return (
    <section className="document-viewer">
      <div className="document-toolbar">
        <div>
          <strong>{url && (isPdf ? <FilePdfOutlined /> : <FileImageOutlined />)} Tài liệu nguồn</strong>
          {!url && <small>Chưa có tài liệu</small>}
        </div>
        {url && !readOnly && (
          <Popconfirm
            title="Gỡ tài liệu khỏi hồ sơ?"
            description="Thay đổi được ghi nhận khi bạn lưu hồ sơ."
            onConfirm={removeDocument}
          >
            <Button size="small">Gỡ tài liệu</Button>
          </Popconfirm>
        )}
      </div>

      {!url && readOnly ? (
        <div className="document-empty read-only-empty">
          <FileImageOutlined />
          <strong>Chưa có tài liệu đối chiếu</strong>
          <p>Hồ sơ này chưa được liên kết PDF hoặc ảnh scan nguồn.</p>
        </div>
      ) : !url ? (
        <div className="document-empty">
          <Upload.Dragger
            accept="image/*,.pdf,application/pdf"
            showUploadList={false}
            disabled={uploading}
            beforeUpload={async (file) => {
              setUploading(true);
              try {
                const uploaded = await documentApi.upload(file);
                onDocumentIdChange?.(uploaded.id);
                setUrl(documentApi.contentUrl(uploaded.id));
                message.success("Đã tải tài liệu lên");
              } catch (error) {
                message.error(error instanceof Error ? error.message : "Không thể tải tài liệu lên");
              } finally {
                setUploading(false);
              }
              return false;
            }}
          >
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">{uploading ? "Đang tải tài liệu..." : "Thả PDF hoặc ảnh scan vào đây"}</p>
            <p className="ant-upload-hint">Hệ thống sẽ lưu và liên kết tài liệu với hồ sơ.</p>
          </Upload.Dragger>
        </div>
      ) : isPdf ? (
        <iframe className="document-frame" src={url} title="Tài liệu PDF" />
      ) : (
        <div className="image-stage"><img src={url} alt="Tài liệu nguồn" /></div>
      )}
    </section>
  );
}
