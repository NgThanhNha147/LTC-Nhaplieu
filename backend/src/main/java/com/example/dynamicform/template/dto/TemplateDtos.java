package com.example.dynamicform.template.dto;

import com.example.dynamicform.template.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}

    public record TemplateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 5000) String description) {}

    public record TemplateResponse(UUID id, String code, String name, String description,
                                   TemplateStatus status, Integer currentVersion, UUID currentVersionId,
                                   Instant createdAt, Instant updatedAt) {}

    public record VersionResponse(UUID id, UUID templateId, Integer versionNo, VersionStatus status,
                                  String physicalSchema, String physicalTable, String configHash,
                                  Instant generatedAt, Instant publishedAt, String storageStatus,
                                  Instant purgedAt, String purgedBy, String purgeReason) {}

    public record GroupRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String code,
            @NotBlank @Size(max = 255) String label,
            @Size(max = 5000) String description,
            @NotNull @Min(0) Integer displayOrder,
            @NotNull @Min(1) @Max(4) Integer columnCount,
            boolean collapsible,
            boolean defaultCollapsed,
            boolean repeatable) {}

    public record GroupResponse(UUID id, String code, String label, String description,
                                Integer displayOrder, Integer columnCount, boolean collapsible,
                                boolean defaultCollapsed, boolean repeatable) {}

    public record RuleRequest(@NotNull RuleType ruleType, Map<String, Object> ruleConfig,
                              @Size(max = 500) String errorMessage, @Min(0) Integer displayOrder) {}

    public record RuleResponse(UUID id, RuleType ruleType, Map<String, Object> ruleConfig,
                               String errorMessage, Integer displayOrder) {}

    public record FormRuleRequest(@NotNull FormRuleType ruleType,
                                  @Size(min = 2, max = 20) List<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String> fieldCodes,
                                  @Size(max = 500) String errorMessage,
                                  @Min(0) Integer displayOrder) {}

    public record FormRuleResponse(UUID id, FormRuleType ruleType, List<String> fieldCodes,
                                   String errorMessage, Integer displayOrder) {}

    public record LookupConfig(UUID lookupSourceId, String lookupCode, String selectionMode,
                               boolean allowClear, boolean autocomplete, Integer minSearchLength,
                               Map<String, Object> dependencyConfig) {}

    public record FieldRequest(
            UUID groupId,
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String fieldCode,
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String columnName,
            @NotBlank @Size(max = 255) String label,
            @NotNull FieldDataType dataType,
            @NotNull ComponentType componentType,
            @Size(max = 5000) String description,
            @Size(max = 255) String placeholder,
            boolean required,
            boolean uniqueValue,
            boolean businessKey,
            boolean indexed,
            boolean searchable,
            boolean sortable,
            boolean exportable,
            @Min(1) @Max(1000000) Integer maxLength,
            @Min(1) @Max(1000) Integer precision,
            @Min(0) @Max(1000) Integer scale,
            String defaultValue,
            @NotNull @Min(0) Integer displayOrder,
            @NotNull @Min(1) @Max(12) Integer gridSpan,
            boolean readOnly,
            boolean hidden,
            @Size(max = 5000) String helpText,
            UUID lookupSourceId,
            LookupConfig lookup,
            @Valid List<RuleRequest> validations) {}

    public record FieldResponse(UUID id, UUID groupId, String fieldCode, String columnName, String label,
                                FieldDataType dataType, ComponentType componentType, String description,
                                String placeholder, boolean required, boolean uniqueValue, boolean businessKey, boolean indexed,
                                boolean searchable, boolean sortable, boolean exportable, Integer maxLength,
                                Integer precision, Integer scale, String defaultValue, Integer displayOrder,
                                Integer gridSpan, boolean readOnly, boolean hidden, String helpText,
                                UUID lookupSourceId, String lookupCode, LookupConfig lookup,
                                List<RuleResponse> validations) {}

    public record FormMetadataResponse(String templateCode, String templateName, Integer version,
                                       UUID versionId, List<FormGroup> groups, List<FormRuleResponse> rules) {}

    public record FormGroup(UUID id, String code, String label, String description, Integer displayOrder,
                            Integer columnCount, boolean collapsible, boolean defaultCollapsed,
                            List<FieldResponse> fields) {}

    public record ValidationIssue(String level, String code, String fieldCode, String message) {}
    public record ValidationResult(boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {}
    public record DdlPreview(String schema, String table, String ddl) {}
    public record MigrationChange(String changeType, String severity, String fieldCode, String label, String message) {}
    public record MigrationPlan(Integer sourceVersion, Integer targetVersion, String sourceTable, String targetTable,
                                long sourceRecordCount, boolean compatible, int addedFields, int modifiedFields,
                                int removedFields, List<MigrationChange> changes, List<String> blockers) {}
    public record StorageCheck(boolean eligible, Integer currentVersion, long sourceRecordCount,
                               long currentRecordCount, long missingRecordCount, List<String> blockers) {}
}
