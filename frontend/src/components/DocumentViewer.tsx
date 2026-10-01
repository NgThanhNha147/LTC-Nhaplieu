import { FileImageOutlined, FilePdfOutlined, InboxOutlined } from "@ant-design/icons";
import { App, Button, Popconfirm, Upload } from "antd";
import { useEffect, useState } from "react";
import { documentApi } from "../api/documents";
import { useAuth } from "../auth/AuthProvider";
import { Permissions } from "../auth/permissions";

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
  const { hasPermission } = useAuth();
  const canUpload = hasPermission(Permissions.DOCUMENT_UPLOAD);
  const canView = hasPermission(Permissions.DOCUMENT_VIEW);
  const [url, setUrl] = useState(initialUrl ?? "");
  const [contentType, setContentType] = useState("");
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    let objectUrl: string | undefined;
    const documentId = initialUrl ? documentApi.idFromContentUrl(initialUrl) : undefined;
    if (!documentId) {
      setUrl(initialUrl ?? "");
      setContentType(initialUrl?.toLowerCase().includes(".pdf") ? "application/pdf" : "");
      return undefined;
    }
    setUrl("");
    void documentApi.contentObjectUrl(documentId).then((content) => {
      objectUrl = content.url;
      setContentType(content.contentType);
      setUrl(content.url);
    }).catch(() => message.error("Không thể tải tài liệu nguồn"));
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [initialUrl, message]);

  const isPdf = contentType.includes("application/pdf") || url.toLowerCase().includes(".pdf")
    || url.startsWith("data:application/pdf")
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
        {url && !readOnly && canUpload && (
          <Popconfirm
            title="Gỡ tài liệu khỏi hồ sơ?"
            description="Thay đổi được ghi nhận khi bạn lưu hồ sơ."
            onConfirm={removeDocument}
          >
            <Button size="small">Gỡ tài liệu</Button>
          </Popconfirm>
        )}
      </div>

      {!canView && url ? (
        <div className="document-empty read-only-empty">
          <FileImageOutlined />
          <strong>Bạn chưa được cấp quyền xem tài liệu</strong>
          <p>Liên hệ quản trị viên nếu bạn cần đối chiếu tài liệu nguồn.</p>
        </div>
      ) : !url && (readOnly || !canUpload) ? (
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
                const content = await documentApi.contentObjectUrl(uploaded.id);
                setContentType(content.contentType || uploaded.contentType);
                setUrl(content.url);
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
