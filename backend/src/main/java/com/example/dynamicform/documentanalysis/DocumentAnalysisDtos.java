package com.example.dynamicform.documentanalysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentAnalysisDtos {
    private DocumentAnalysisDtos() {}

    public record SuggestedField(
            @NotBlank String id,
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String fieldCode,
            @NotBlank @Size(max = 255) String label,
            @NotBlank String dataType,
            @NotBlank String componentType,
            String value,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            @Min(1) Integer page,
            @Size(max = 2000) String evidence,
            boolean required,
            boolean selected) {}

    public record SuggestedGroup(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String code,
            @NotBlank @Size(max = 255) String label,
            @Min(0) int displayOrder,
            @NotEmpty List<@Valid SuggestedField> fields) {}

    public record SuggestedForm(List<@Valid SuggestedGroup> groups) {}

    public record TemplateMatch(UUID templateId, UUID versionId, String templateCode,
                                String templateName, Integer versionNo, BigDecimal confidence) {}

    public record AnalysisResponse(
            UUID id,
            UUID documentId,
            String documentName,
            String documentUrl,
            AnalysisStatus status,
            DocumentSourceKind sourceKind,
            String documentType,
            BigDecimal confidence,
            SuggestedForm suggestedForm,
            SuggestedForm reviewedForm,
            TemplateMatch templateMatch,
            UUID draftRecordId,
            String errorMessage,
            Long rowVersion,
            Instant createdAt,
            Instant updatedAt) {}

    public record ReviewRequest(@NotNull @Valid SuggestedForm form, @NotNull @Min(0) Long rowVersion) {}

    public record PromoteRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String templateCode,
            @NotBlank @Size(max = 255) String templateName,
            @Size(max = 5000) String description,
            @NotNull @Min(0) Long rowVersion) {}

    public record PromoteResponse(UUID templateId, UUID versionId, String templateCode,
                                  String templateName, int groupCount, int fieldCount) {}
}
