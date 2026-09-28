package com.example.dynamicform.document;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
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
    private final CurrentUser currentUser;
    private final DocumentOcrService ocrService;
    @Value("${app.storage.path:./data/documents}") private String configuredRoot;
    @Value("${app.ocr.enabled:false}") private boolean ocrEnabled;
    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(configuredRoot).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public DocumentResponse upload(MultipartFile file) {
        if (file.isEmpty()) throw ApiException.badRequest("File rỗng");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) throw ApiException.badRequest("Chỉ hỗ trợ PDF/JPEG/PNG/WEBP/TIFF");
        UUID id = UUID.randomUUID(); String storedName = id.toString(); Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw ApiException.badRequest("Đường dẫn lưu file không hợp lệ");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            DocumentFile entity = new DocumentFile(); entity.setId(id);
            entity.setOriginalName(safeOriginalName(file.getOriginalFilename())); entity.setStoredName(storedName);
            entity.setContentType(contentType); entity.setSizeBytes(file.getSize()); entity.setChecksumSha256(checksum(target));
            entity.setStoragePath(target.toString()); entity.setCreatedAt(Instant.now()); entity.setCreatedBy(currentUser.username());
            repository.save(entity);
            // save() commits before OCR starts because upload is deliberately not one long transaction.
            if (ocrEnabled && ocrService.prepare(id)) ocrService.enqueue(id);
            return map(entity);
        } catch (IOException e) { throw new IllegalStateException("Không thể lưu file", e); }
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(UUID id) {
        DocumentFile entity = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài liệu"));
        try {
            Path path = Paths.get(entity.getStoragePath()).toAbsolutePath().normalize();
            if (!path.startsWith(root)) throw ApiException.badRequest("Đường dẫn tài liệu không hợp lệ");
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) throw ApiException.notFound("File tài liệu không còn tồn tại");
            return new DocumentDownload(map(entity), resource);
        } catch (java.net.MalformedURLException e) { throw new IllegalStateException("Không thể đọc file", e); }
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
}
