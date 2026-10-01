package com.example.dynamicform.batch;

import com.example.dynamicform.batch.BatchDtos.*;
import com.example.dynamicform.security.FunctionGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {
    private final BatchWorkflowService service;
    private final BatchExportService exportService;
    private final FunctionGuard functions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatchResponse create(@Valid @RequestBody CreateBatchRequest request) {
        functions.require("BATCH_CREATE");
        return service.create(request);
    }

    @GetMapping
    public BatchPage list(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) UUID templateId,
                          @RequestParam(required = false) UUID templateVersionId,
                          @RequestParam(required = false) UUID assignedUserId,
                          @RequestParam(required = false) Boolean archived,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, templateId, templateVersionId, assignedUserId, archived, page, size);
    }

    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable UUID id) { return service.getBatch(id); }

    @GetMapping("/{id}/statistics")
    public BatchStatistics statistics(@PathVariable UUID id) { return service.statistics(id); }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) WorkflowStatus status,
                                         @RequestParam(required = false) LocalDate fromDate,
                                         @RequestParam(required = false) LocalDate toDate) {
        functions.require("RECORD_EXPORT");
        var file = exportService.export(id, status, fromDate, toDate, format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @PostMapping("/{id}/assign")
    public BatchResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignBatchRequest request) {
        functions.require("BATCH_ASSIGN");
        return service.assign(id, request);
    }

    @PostMapping("/{id}/archive")
    public BatchResponse archive(@PathVariable UUID id, @RequestParam long rowVersion) {
        functions.require("BATCH_ARCHIVE");
        return service.archive(id, rowVersion);
    }

    @PostMapping(value = {"/{id}/documents", "/{id}/upload/files"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchUploadResponse uploadFiles(@PathVariable UUID id,
                                           @RequestParam(value = "sourceType", required = false) String sourceType,
                                           @RequestPart("files") List<MultipartFile> files,
                                           @RequestParam(value = "relativePaths", required = false) List<String> relativePaths) {
        functions.require("BATCH_UPLOAD");
        BatchSourceType type = sourceType == null || sourceType.isBlank() ? null
                : BatchSourceType.valueOf(sourceType.trim().toUpperCase());
        return service.uploadFiles(id, type, files, relativePaths);
    }

    @PostMapping(value = "/{id}/upload/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchUploadResponse uploadZip(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        functions.require("BATCH_UPLOAD");
        return service.uploadZip(id, file);
    }
}
