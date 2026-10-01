package com.example.dynamicform.document;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.batch.BatchDocumentRepository;
import com.example.dynamicform.security.SecurityRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp", "image/tiff");
    private final DocumentRepository repository;
    private final BatchDocumentRepository batchDocuments;
    private final SecurityRepository security;
    private final CurrentUser currentUser;
    private final DocumentOcrService ocrService;
    private final FileSignatureValidator fileSignatureValidator;
    @Value("${app.storage.path:./data/documents}") private String configuredRoot;
    @Value("${app.ocr.enabled:false}") private boolean ocrEnabled;
    @Value("${app.security.enabled:true}") private boolean securityEnabled;
    @Value("${app.storage.max-file-bytes:52428800}") private long maxFileBytes;
    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(configuredRoot).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public DocumentResponse upload(MultipartFile file) {
        if (file.isEmpty()) throw ApiException.badRequest("File rỗng");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw ApiException.badRequest("Chỉ hỗ trợ PDF/JPEG/PNG/WEBP/TIFF");
        }
        try (InputStream in = file.getInputStream()) {
            return store(file.getOriginalFilename(), contentType, file.getSize(), in, maxFileBytes, true);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đọc file", e);
        }
    }

    public DocumentResponse uploadPdf(String originalName, long declaredSize, InputStream input, long limitBytes) {
        return store(originalName, "application/pdf", declaredSize, input, limitBytes, false);
    }

    private DocumentResponse store(String originalName, String contentType, long declaredSize, InputStream input,
                                   long limitBytes, boolean enqueueOcr) {
        if (declaredSize == 0) throw ApiException.badRequest("File rỗng");
        if (declaredSize > limitBytes) throw ApiException.badRequest("File vượt quá dung lượng cho phép");
        UUID id = UUID.randomUUID();
        String storedName = id.toString();
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw ApiException.badRequest("Đường dẫn lưu file không hợp lệ");
        try (PushbackInputStream checked = new PushbackInputStream(new LimitedInputStream(input, limitBytes), 1024)) {
            byte[] header = checked.readNBytes(1024);
            if (header.length == 0) throw ApiException.badRequest("File rỗng");
            fileSignatureValidator.validate(contentType, header);
            checked.unread(header);
            Files.copy(checked, target, StandardCopyOption.REPLACE_EXISTING);
            DocumentFile entity = new DocumentFile();
            entity.setId(id);
            entity.setOriginalName(safeOriginalName(originalName));
            entity.setStoredName(storedName);
            entity.setContentType(contentType);
            entity.setSizeBytes(Files.size(target));
            entity.setChecksumSha256(checksum(target));
            entity.setStoragePath(target.toString());
            entity.setCreatedAt(Instant.now());
            entity.setCreatedBy(currentUser.username());
            repository.save(entity);
            if (enqueueOcr && ocrEnabled && ocrService.prepare(id)) ocrService.enqueue(id);
            return map(entity);
        } catch (IOException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            throw new IllegalStateException("Không thể lưu file", e);
        }
    }

    public void removeUnused(UUID id) {
        repository.findById(id).ifPresent(entity -> {
            try {
                Path path = Paths.get(entity.getStoragePath()).toAbsolutePath().normalize();
                if (path.startsWith(root)) Files.deleteIfExists(path);
            } catch (IOException ignored) {}
            repository.delete(entity);
        });
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(UUID id) {
        assertCanView(id);
        DocumentFile entity = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài liệu"));
        try {
            Path path = Paths.get(entity.getStoragePath()).toAbsolutePath().normalize();
            if (!path.startsWith(root)) throw ApiException.badRequest("Đường dẫn tài liệu không hợp lệ");
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) throw ApiException.notFound("File tài liệu không còn tồn tại");
            return new DocumentDownload(map(entity), resource);
        } catch (java.net.MalformedURLException e) { throw new IllegalStateException("Không thể đọc file", e); }
    }

    void assertCanView(UUID documentId) {
        if (!securityEnabled) return;
        String username = currentUser.username();
        if (security.hasFunction(username, "BATCH_ASSIGN")
                || security.hasFunction(username, "RECORD_APPROVE")
                || security.hasFunction(username, "RECORD_EXPORT")) {
            return;
        }
        UUID userId = security.findUserByUsername(username).map(SecurityRepository.UserRow::id)
                .orElseThrow(() -> ApiException.forbidden("Không xác định được tài khoản hiện tại."));
        if (!batchDocuments.existsByDocumentFileIdAndDeletedFalseAndAssignedUserId(documentId, userId)) {
            throw ApiException.notFound("Không tìm thấy tài liệu hoặc tài liệu không thuộc hồ sơ được giao.");
        }
    }

    private String checksum(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) >= 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String safeOriginalName(String name) { return name == null ? "document" : Paths.get(name).getFileName().toString().replaceAll("[\\r\\n]", "_"); }
    DocumentResponse map(DocumentFile d) {
        return new DocumentResponse(d.getId(), d.getOriginalName(), d.getContentType(), d.getSizeBytes(),
                d.getChecksumSha256(), d.getCreatedAt(), "/api/v1/documents/" + d.getId() + "/content",
                d.getOcrStatus(), d.getOcrEngine(), d.getOcrError(), d.getOcrStartedAt(), d.getOcrCompletedAt(),
                d.getOcrPdfPath() == null ? null : "/api/v1/documents/" + d.getId() + "/ocr/content");
    }
    public record DocumentResponse(UUID id, String originalName, String contentType, Long sizeBytes,
                                   String checksumSha256, Instant createdAt, String contentUrl,
                                   String ocrStatus, String ocrEngine, String ocrError,
                                   Instant ocrStartedAt, Instant ocrCompletedAt, String ocrContentUrl) {}
    public record DocumentDownload(DocumentResponse metadata, Resource resource) {}

    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private final long limit;
        private long count;

        private LimitedInputStream(InputStream in, long limit) { super(in); this.limit = limit; }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) increment(1);
            return value;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read > 0) increment(read);
            return read;
        }

        private void increment(int amount) throws IOException {
            count += amount;
            if (count > limit) throw new IOException("FILE_SIZE_LIMIT_EXCEEDED");
        }
    }
}
