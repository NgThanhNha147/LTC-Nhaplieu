package com.example.dynamicform.document;

import com.example.dynamicform.document.DocumentService.DocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService service;
    private final DocumentOcrService ocrService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file) { return service.upload(file); }

    @GetMapping("/{id}/content")
    public ResponseEntity<org.springframework.core.io.Resource> content(@PathVariable UUID id) {
        var download = service.download(id); var metadata = download.metadata();
        MediaType type;
        try { type = MediaType.parseMediaType(metadata.contentType()); } catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = ContentDisposition.inline().filename(metadata.originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(type).contentLength(metadata.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(download.resource());
    }

    @PostMapping("/{id}/ocr")
    public DocumentOcrService.DocumentOcrResponse startOcr(@PathVariable UUID id) {
        if (ocrService.prepare(id)) ocrService.enqueue(id);
        return ocrService.status(id, false);
    }

    @GetMapping("/{id}/ocr")
    public DocumentOcrService.DocumentOcrResponse ocrStatus(@PathVariable UUID id,
                                                             @RequestParam(defaultValue = "false") boolean includeText) {
        return ocrService.status(id, includeText);
    }

    @GetMapping("/{id}/ocr/content")
    public ResponseEntity<org.springframework.core.io.Resource> ocrContent(@PathVariable UUID id) {
        Path path = ocrService.ocrPdf(id);
        try {
            var resource = new org.springframework.core.io.FileSystemResource(path);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(Files.size(path))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("ocr-" + id + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(resource);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Không thể đọc file OCR", e);
        }
    }
}
