package com.example.dynamicform.document;

import com.example.dynamicform.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

/** Asynchronous adapter to the isolated Vietnamese OCR container. */
@Service
@RequiredArgsConstructor
public class DocumentOcrService {
    private final DocumentRepository repository;
    @Value("${app.storage.path:./data/documents}") private String configuredRoot;
    @Value("${app.ocr.service-url:http://localhost:8090}") private String ocrServiceUrl;

    @Async("ocrTaskExecutor")
    public void enqueue(UUID documentId) {
        try { process(documentId); }
        catch (Exception ignored) { /* process persists a user-facing failure */ }
    }

    @Transactional
    public boolean prepare(UUID documentId) {
        DocumentFile document = repository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài liệu"));
        if ("COMPLETED".equals(document.getOcrStatus())) return false;
        if ("PROCESSING".equals(document.getOcrStatus()) && document.getOcrStartedAt() != null
                && Duration.between(document.getOcrStartedAt(), Instant.now()).compareTo(Duration.ofMinutes(10)) < 0) return false;
        document.setOcrStatus("PROCESSING");
        document.setOcrEngine("ocr-service:tesseract-vie");
        document.setOcrStartedAt(Instant.now());
        document.setOcrCompletedAt(null);
        document.setOcrError(null);
        repository.save(document);
        return true;
    }

    public void process(UUID documentId) {
        DocumentFile document = repository.findById(documentId).orElse(null);
        if (document == null) return;
        Path root = Paths.get(configuredRoot).toAbsolutePath().normalize();
        Path input = safePath(document.getStoragePath(), root);
        if (input == null) { fail(document, "Đường dẫn tài liệu không hợp lệ"); return; }
        Path output = root.resolve(documentId + ".ocr.pdf").normalize();
        if (!output.startsWith(root)) { fail(document, "Đường dẫn OCR không hợp lệ"); return; }
        try {
            if (!"application/pdf".equalsIgnoreCase(document.getContentType()))
                throw new IOException("OCR hiện hỗ trợ PDF; vui lòng tải tài liệu PDF");
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(input));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<byte[]> response = new RestTemplate().postForEntity(ocrServiceUrl + "/ocr/pdf",
                    new HttpEntity<>(parts, headers), byte[].class);
            byte[] body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.length == 0)
                throw new IOException("OCR service không trả về file kết quả");
            Files.write(output, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            document.setOcrPdfPath(output.toString());
            document.setOcrTextPath(null);
            document.setOcrStatus("COMPLETED");
            document.setOcrCompletedAt(Instant.now());
            repository.save(document);
        } catch (Exception e) {
            fail(document, friendlyMessage(e));
        }
    }

    @Transactional(readOnly = true)
    public DocumentOcrResponse status(UUID id, boolean includeText) {
        DocumentFile d = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài liệu"));
        return new DocumentOcrResponse(d.getId(), d.getOcrStatus(), d.getOcrEngine(), d.getOcrError(),
                d.getOcrStartedAt(), d.getOcrCompletedAt(), null,
                d.getOcrPdfPath() == null ? null : "/api/v1/documents/" + id + "/ocr/content");
    }

    @Transactional(readOnly = true)
    public Path ocrPdf(UUID id) {
        DocumentFile d = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài liệu"));
        if (d.getOcrPdfPath() == null || !"COMPLETED".equals(d.getOcrStatus()))
            throw ApiException.notFound("Tài liệu OCR chưa sẵn sàng");
        Path path = safePath(d.getOcrPdfPath(), Paths.get(configuredRoot).toAbsolutePath().normalize());
        if (path == null || !Files.exists(path)) throw ApiException.notFound("File OCR không còn tồn tại");
        return path;
    }

    private Path safePath(String value, Path root) {
        try {
            Path path = Paths.get(value).toAbsolutePath().normalize();
            return path.startsWith(root) ? path : null;
        } catch (Exception e) { return null; }
    }

    private void fail(DocumentFile d, String message) {
        d.setOcrStatus("FAILED");
        d.setOcrError(message.length() > 1900 ? message.substring(0, 1900) : message);
        d.setOcrCompletedAt(Instant.now());
        repository.save(d);
    }

    private String friendlyMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "Không thể nhận dạng văn bản. Kiểm tra lại file hoặc dịch vụ OCR.";
        if (message.contains("Connection refused") || message.contains("I/O error")) return "Dịch vụ OCR chưa sẵn sàng, vui lòng thử lại sau.";
        return "Không thể nhận dạng văn bản: " + message.replaceAll("\\s+", " ").trim();
    }

    public record DocumentOcrResponse(UUID documentId, String status, String engine, String error,
                                      Instant startedAt, Instant completedAt, String text, String ocrPdfUrl) {}
}
