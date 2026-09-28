package com.example.dynamicform.export;

import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.SearchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/templates/{templateCode}/records/export")
@RequiredArgsConstructor
public class ExportController {
    private final ExportService service;
    @PostMapping
    public ResponseEntity<byte[]> export(@PathVariable String templateCode,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @Valid @RequestBody SearchRequest request) {
        var file = service.export(templateCode, request, format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
