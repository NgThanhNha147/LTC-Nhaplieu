package com.example.dynamicform.documentanalysis;

import com.example.dynamicform.documentanalysis.DocumentAnalysisDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentAnalysisController {
    private final DocumentAnalysisService service;

    @PostMapping("/documents/{documentId}/analyses")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse analyze(@PathVariable UUID documentId) { return service.analyze(documentId); }

    @GetMapping("/document-analyses")
    public List<AnalysisResponse> recent() { return service.recent(); }

    @GetMapping("/document-analyses/{id}")
    public AnalysisResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/document-analyses/{id}/review")
    public AnalysisResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return service.review(id, request);
    }

    @PostMapping("/document-analyses/{id}/promote")
    @ResponseStatus(HttpStatus.CREATED)
    public PromoteResponse promote(@PathVariable UUID id, @Valid @RequestBody PromoteRequest request) {
        return service.promote(id, request);
    }
}
