package com.example.dynamicform.schema;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.dto.TemplateDtos.DdlPreview;
import com.example.dynamicform.template.dto.TemplateDtos.MigrationChange;
import com.example.dynamicform.template.dto.TemplateDtos.MigrationPlan;
import com.example.dynamicform.template.dto.TemplateDtos.ValidationResult;
import com.example.dynamicform.template.dto.TemplateDtos.StorageCheck;
import com.example.dynamicform.template.repository.*;
import com.example.dynamicform.template.service.TemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SchemaGeneratorService {
    private final TemplateVersionRepository versions;
    private final FieldDefinitionRepository fields;
    private final ValidationRuleRepository rules;
    private final com.example.dynamicform.template.repository.TemplateRuleRepository templateRules;
    private final TemplateService templateService;
    private final MetadataValidator validator;
    private final MigrationPlanner migrationPlanner;
    private final IdentifierPolicy identifiers;
    private final TemplateOperationLock operationLock;
    private final CurrentUser currentUser;
    private final ConfigurationHasher configurationHasher;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public ValidationResult validate(UUID versionId) {
        TemplateVersion version = versions.findByIdForUpdate(versionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        templateService.requireMutable(version);
        var result = validationResult(versionId);
        version.setStatus(result.valid() ? VersionStatus.VALIDATED : VersionStatus.DRAFT);
        return result;
    }

    @Transactional(readOnly = true)
    public DdlPreview preview(UUID versionId) {
        TemplateVersion version = templateService.requireVersion(versionId);
        Plan plan = buildPlan(version);
        return new DdlPreview(plan.schema(), plan.table(), String.join(";\n\n", plan.statements()) + ";");
    }

    @Transactional(readOnly = true)
    public MigrationPlan migrationPlan(UUID versionId) {
        TemplateVersion version = templateService.requireVersion(versionId);
        String targetTable = version.getPhysicalTable() == null ? physicalTableName(version) : version.getPhysicalTable();
        MigrationDetails details = migrationDetails(version);
        return mapMigrationPlan(version, details, targetTable);
    }

    @Transactional
    public TemplateVersion generate(UUID versionId) {
        TemplateVersion version = versions.findByIdForUpdate(versionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        templateService.requireMutable(version);
        Plan plan = buildPlan(version);
        MigrationDetails migration = migrationDetails(version);
        if (!migration.analysis().compatible()) {
            throw ApiException.badRequest("Chưa thể chuyển dữ liệu cũ: " + migration.analysis().blockers().getFirst());
        }
        Integer exists = jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name=?",
                Integer.class, plan.schema(), plan.table());
        if (exists != null && exists > 0) throw ApiException.conflict("Tên bảng dữ liệu đã tồn tại trong hệ thống: " + plan.schema() + "." + plan.table());
        jdbc.execute("select pg_advisory_xact_lock(hashtext('dynamic-form:' || '" + plan.table() + "'))");
        jdbc.execute(plan.statements().getFirst());
        copyData(migration, plan.schema(), plan.table());
        plan.statements().stream().skip(1).forEach(jdbc::execute);
        version.setPhysicalSchema(plan.schema());
        version.setPhysicalTable(plan.table());
        version.setConfigHash(configHash(versionId));
        version.setGeneratedAt(Instant.now());
        version.setStatus(VersionStatus.GENERATED);
        return version;
    }

    @Transactional
    public TemplateVersion publish(UUID versionId) {
        TemplateVersion requested = versions.findById(versionId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        operationLock.lockForPublish(requested.getTemplate().getCode());
        TemplateVersion version = versions.findByIdForUpdate(versionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        if (version.getStatus() != VersionStatus.GENERATED && version.getStatus() != VersionStatus.PUBLISHED) {
            throw ApiException.conflict("Cần tạo bảng dữ liệu trước khi đưa biểu mẫu vào sử dụng.");
        }
        String currentHash = configHash(versionId);
        if (!Objects.equals(version.getConfigHash(), currentHash)
                && version.getStatus() == VersionStatus.GENERATED
                && configurationHasher.isLegacyHash(version.getConfigHash())) {
            // Generated versions are immutable; upgrade hashes produced by the old non-deterministic algorithm.
            version.setConfigHash(currentHash);
        }
        if (!Objects.equals(version.getConfigHash(), currentHash)) throw ApiException.conflict("Cấu hình trường đã thay đổi sau khi tạo bảng. Hãy kiểm tra và tạo lại bảng dữ liệu.");
        synchronizeBeforePublish(version);
        version.setStatus(VersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        FormTemplate template = version.getTemplate();
        versions.findByTemplateIdOrderByVersionNoDesc(template.getId()).stream()
                .filter(item -> !item.getId().equals(version.getId()) && item.getStatus() == VersionStatus.PUBLISHED)
                .forEach(item -> item.setStatus(VersionStatus.ARCHIVED));
        template.setStatus(TemplateStatus.PUBLISHED);
        template.setCurrentVersion(version.getVersionNo());
        return version;
    }

    @Transactional(readOnly = true)
    public StorageCheck storageCheck(UUID versionId) {
        TemplateVersion version = templateService.requireVersion(versionId);
        List<String> blockers = new ArrayList<>();
        if (version.getStatus() == VersionStatus.PUBLISHED) blockers.add("Không thể giải phóng phiên bản đang được sử dụng.");
        if (version.getStatus() != VersionStatus.ARCHIVED) blockers.add("Chỉ phiên bản cũ đã lưu trữ mới được giải phóng bảng.");
        if (version.getPhysicalSchema() == null || version.getPhysicalTable() == null) blockers.add("Phiên bản chưa có bảng dữ liệu để giải phóng.");
        TemplateVersion current = versions.findFirstByTemplateCodeIgnoreCaseAndStatusOrderByVersionNoDesc(version.getTemplate().getCode(), VersionStatus.PUBLISHED).orElse(null);
        if (current == null) blockers.add("Biểu mẫu chưa có phiên bản đang sử dụng để đối chiếu.");
        long sourceCount = 0, currentCount = 0, missing = 0;
        if (blockers.isEmpty()) {
            String source = qualified(version);
            sourceCount = countRows(source);
            currentCount = countRows(qualified(current));
            missing = jdbc.queryForObject("SELECT COUNT(*) FROM " + source + " old WHERE NOT EXISTS (SELECT 1 FROM " + qualified(current) + " now WHERE now.id=old.id)", Long.class);
            if (missing > 0) blockers.add("Còn " + missing + " hồ sơ chưa tồn tại ở phiên bản đang sử dụng.");
        }
        return new StorageCheck(blockers.isEmpty(), current == null ? null : current.getVersionNo(), sourceCount, currentCount, missing, blockers);
    }

    @Transactional
    public TemplateVersion cancelGenerated(UUID versionId) {
        TemplateVersion version = versions.findByIdForUpdate(versionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        if (version.getStatus() != VersionStatus.GENERATED) throw ApiException.conflict("Chỉ có thể hủy bảng của phiên bản đã tạo bảng nhưng chưa đưa vào sử dụng.");
        if (version.getPhysicalSchema() == null || version.getPhysicalTable() == null) throw ApiException.conflict("Phiên bản chưa có bảng dữ liệu.");
        operationLock.lockForPublish(version.getTemplate().getCode());
        jdbc.execute("DROP TABLE " + qualified(version));
        version.setPhysicalSchema(null); version.setPhysicalTable(null); version.setConfigHash(null); version.setGeneratedAt(null); version.setStatus(VersionStatus.VALIDATED); version.setStorageStatus("ACTIVE");
        return version;
    }

    @Transactional
    public TemplateVersion purgeArchived(UUID versionId, String reason) {
        TemplateVersion version = versions.findByIdForUpdate(versionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        StorageCheck check = storageCheck(versionId);
        if (!check.eligible()) throw ApiException.conflict(String.join(" ", check.blockers()));
        operationLock.lockForPublish(version.getTemplate().getCode());
        StorageCheck lockedCheck = storageCheck(versionId);
        if (!lockedCheck.eligible()) throw ApiException.conflict(String.join(" ", lockedCheck.blockers()));
        jdbc.execute("DROP TABLE " + qualified(version));
        version.setStorageStatus("PURGED"); version.setPurgedAt(Instant.now()); version.setPurgedBy(currentUser.username());
        version.setPurgeReason(reason == null || reason.isBlank() ? "Đã kiểm tra dữ liệu chuyển sang phiên bản đang sử dụng." : reason.trim());
        return version;
    }

    private String qualified(TemplateVersion version) {
        return identifiers.requireValid(version.getPhysicalSchema(), "Schema dữ liệu") + "." + identifiers.requireValid(version.getPhysicalTable(), "Bảng dữ liệu");
    }

    private long countRows(String table) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    private void synchronizeBeforePublish(TemplateVersion version) {
        MigrationDetails initial = migrationDetails(version);
        if (initial.sourceVersion() == null) return;
        String sourceSchema = identifiers.requireValid(initial.sourceVersion().getPhysicalSchema(), "Schema nguồn");
        String sourceTable = identifiers.requireValid(initial.sourceVersion().getPhysicalTable(), "Bảng nguồn");
        jdbc.execute("LOCK TABLE " + sourceSchema + "." + sourceTable + " IN SHARE MODE");

        MigrationDetails latest = migrationDetails(version);
        if (!latest.analysis().compatible()) {
            throw ApiException.badRequest("Chưa thể chuyển dữ liệu cũ: " + latest.analysis().blockers().getFirst());
        }
        String targetSchema = identifiers.requireValid(version.getPhysicalSchema(), "Schema đích");
        String targetTable = identifiers.requireValid(version.getPhysicalTable(), "Bảng đích");
        jdbc.execute("TRUNCATE TABLE " + targetSchema + "." + targetTable);
        copyData(latest, targetSchema, targetTable);
    }

    private Plan buildPlan(TemplateVersion version) {
        ValidationResult validation = validationResult(version.getId());
        if (!validation.valid()) throw ApiException.badRequest("Chưa thể tạo bảng dữ liệu. Hãy bấm 'Kiểm tra cấu hình bảng' và xử lý các mục được hướng dẫn.");
        String schema = identifiers.requireValid("app_data", "Schema");
        String table = physicalTableName(version);
        List<FieldDefinition> fieldList = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(version.getId());
        List<String> definitions = new ArrayList<>();
        definitions.add("id UUID PRIMARY KEY");
        definitions.add("source_document_id UUID REFERENCES app_meta.document_file(id)");
        for (FieldDefinition f : fieldList) {
            definitions.add(identifiers.requireValid(f.getColumnName(), "Tên cột") + " " + columnDefinition(f));
        }
        definitions.add("record_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'");
        definitions.add("created_at TIMESTAMPTZ NOT NULL");
        definitions.add("created_by VARCHAR(100) NOT NULL");
        definitions.add("updated_at TIMESTAMPTZ NOT NULL");
        definitions.add("updated_by VARCHAR(100) NOT NULL");
        definitions.add("row_version BIGINT NOT NULL DEFAULT 0");
        definitions.add("deleted BOOLEAN NOT NULL DEFAULT FALSE");
        definitions.add("CONSTRAINT " + constraintName(table, "record_status")
                + " CHECK (record_status IN ('DRAFT','COMPLETED'))");
        for (FieldDefinition field : fieldList) {
            if (Boolean.TRUE.equals(field.getRequired())) {
                definitions.add("CONSTRAINT " + constraintName(table, field.getColumnName())
                        + " CHECK (deleted = TRUE OR record_status <> 'COMPLETED' OR " + field.getColumnName() + " IS NOT NULL)");
            }
        }
        String create = "CREATE TABLE " + schema + "." + table + " (\n  "
                + String.join(",\n  ", definitions) + "\n)";
        List<String> statements = new ArrayList<>();
        statements.add(create);
        for (FieldDefinition f : fieldList) {
            if (Boolean.TRUE.equals(f.getBusinessKey())) {
                statements.add("CREATE UNIQUE INDEX " + indexName(table, f.getColumnName(), "bk") + " ON " + schema + "." + table
                        + " (" + f.getColumnName() + ")");
            } else if (f.getUniqueValue()) {
                statements.add("CREATE UNIQUE INDEX " + indexName(table, f.getColumnName(), "uq") + " ON " + schema + "." + table
                        + " (" + f.getColumnName() + ") WHERE deleted = FALSE");
            } else if (f.getIndexed()) {
                statements.add("CREATE INDEX " + indexName(table, f.getColumnName(), "idx") + " ON " + schema + "." + table
                        + " (" + f.getColumnName() + ") WHERE deleted = FALSE");
            }
        }
        statements.add("CREATE INDEX " + indexName(table, "created_at", "idx") + " ON " + schema + "." + table + " (created_at DESC) WHERE deleted = FALSE");
        return new Plan(schema, table, statements);
    }

    private ValidationResult validationResult(UUID versionId) {
        List<FieldDefinition> fieldList = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId);
        return validator.validate(fieldList, rules.findByFieldIdIn(fieldList.stream().map(FieldDefinition::getId).toList()),
                templateRules.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId));
    }

    private MigrationDetails migrationDetails(TemplateVersion targetVersion) {
        List<FieldDefinition> targetFields = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(targetVersion.getId());
        TemplateVersion sourceVersion = versions
                .findFirstByTemplateCodeIgnoreCaseAndStatusOrderByVersionNoDesc(targetVersion.getTemplate().getCode(), VersionStatus.PUBLISHED)
                .filter(item -> !item.getId().equals(targetVersion.getId()))
                .orElse(null);
        if (sourceVersion == null) {
            return new MigrationDetails(null, List.of(), targetFields, 0, migrationPlanner.analyze(List.of(), targetFields, 0));
        }
        if (sourceVersion.getPhysicalSchema() == null || sourceVersion.getPhysicalTable() == null) {
            throw ApiException.conflict("Phiên bản đang sử dụng chưa có bảng dữ liệu để chuyển.");
        }
        String sourceSchema = identifiers.requireValid(sourceVersion.getPhysicalSchema(), "Schema nguồn");
        String sourceTable = identifiers.requireValid(sourceVersion.getPhysicalTable(), "Bảng nguồn");
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + sourceSchema + "." + sourceTable, Long.class);
        Long completedCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + sourceSchema + "." + sourceTable
                + " WHERE deleted=FALSE AND record_status='COMPLETED'", Long.class);
        long sourceRecordCount = count == null ? 0 : count;
        List<FieldDefinition> sourceFields = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(sourceVersion.getId());
        MigrationPlanner.Analysis analysis = migrationPlanner.analyze(sourceFields, targetFields, sourceRecordCount,
                completedCount == null ? 0 : completedCount);
        analysis = addRequiredConstraintChecks(sourceVersion, sourceFields, targetFields, analysis);
        analysis = addUniqueConstraintChecks(sourceVersion, sourceFields, targetFields, analysis);
        return new MigrationDetails(sourceVersion, sourceFields, targetFields, sourceRecordCount, analysis);
    }

    private MigrationPlanner.Analysis addUniqueConstraintChecks(TemplateVersion sourceVersion,
                                                                 List<FieldDefinition> sourceFields,
                                                                 List<FieldDefinition> targetFields,
                                                                 MigrationPlanner.Analysis analysis) {
        if (!analysis.compatible()) return analysis;
        List<MigrationChange> changes = new ArrayList<>(analysis.changes());
        List<String> blockers = new ArrayList<>(analysis.blockers());
        String sourceSchema = identifiers.requireValid(sourceVersion.getPhysicalSchema(), "Schema nguồn");
        String sourceTable = identifiers.requireValid(sourceVersion.getPhysicalTable(), "Bảng nguồn");
        for (FieldDefinition target : targetFields) {
            FieldDefinition source = matchingSourceField(sourceFields, target);
            if (source == null || !Boolean.TRUE.equals(target.getUniqueValue())) continue;
            String column = identifiers.requireValid(source.getColumnName(), "Cột kiểm tra unique");
            String expression = shouldBackfillDefault(source, target)
                    ? "COALESCE(" + column + ", " + defaultLiteral(target) + ")" : column;
            String activeScope = Boolean.TRUE.equals(target.getBusinessKey()) ? "" : "deleted=FALSE AND ";
            Integer duplicateGroups = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT " + expression + " AS checked_value FROM "
                    + sourceSchema + "." + sourceTable + " WHERE " + activeScope + expression
                    + " IS NOT NULL GROUP BY " + expression + " HAVING COUNT(*) > 1) duplicated", Integer.class);
            if (duplicateGroups != null && duplicateGroups > 0) {
                String message = "Trường '" + target.getLabel() + "' đang có dữ liệu trùng nên chưa thể bật tùy chọn Không được trùng.";
                blockers.add(message);
                changes.add(new MigrationChange("MODIFIED", "BLOCKING", target.getFieldCode(), target.getLabel(), message));
            }
        }
        return new MigrationPlanner.Analysis(blockers.isEmpty(), analysis.addedFields(), analysis.modifiedFields(),
                analysis.removedFields(), changes, blockers);
    }

    private MigrationPlanner.Analysis addRequiredConstraintChecks(TemplateVersion sourceVersion,
                                                                   List<FieldDefinition> sourceFields,
                                                                   List<FieldDefinition> targetFields,
                                                                   MigrationPlanner.Analysis analysis) {
        if (!analysis.compatible()) return analysis;
        List<MigrationChange> changes = new ArrayList<>(analysis.changes());
        List<String> blockers = new ArrayList<>(analysis.blockers());
        String sourceSchema = identifiers.requireValid(sourceVersion.getPhysicalSchema(), "Schema nguồn");
        String sourceTable = identifiers.requireValid(sourceVersion.getPhysicalTable(), "Bảng nguồn");
        for (FieldDefinition target : targetFields) {
            if (!Boolean.TRUE.equals(target.getRequired())) continue;
            FieldDefinition source = matchingSourceField(sourceFields, target);
            if (source == null || shouldBackfillDefault(source, target)) continue;
            String column = identifiers.requireValid(source.getColumnName(), "Cột kiểm tra bắt buộc");
            Long missing = jdbc.queryForObject("SELECT COUNT(*) FROM " + sourceSchema + "." + sourceTable
                    + " WHERE deleted=FALSE AND record_status='COMPLETED' AND " + column + " IS NULL", Long.class);
            if (missing != null && missing > 0) {
                String message = "Trường '" + target.getLabel() + "' đang trống trong " + missing
                        + " hồ sơ đã hoàn tất.";
                blockers.add(message);
                changes.add(new MigrationChange("MODIFIED", "BLOCKING", target.getFieldCode(), target.getLabel(), message));
            }
        }
        return new MigrationPlanner.Analysis(blockers.isEmpty(), analysis.addedFields(), analysis.modifiedFields(),
                analysis.removedFields(), changes, blockers);
    }

    private MigrationPlan mapMigrationPlan(TemplateVersion targetVersion, MigrationDetails details, String targetTable) {
        TemplateVersion source = details.sourceVersion();
        MigrationPlanner.Analysis analysis = details.analysis();
        return new MigrationPlan(source == null ? null : source.getVersionNo(), targetVersion.getVersionNo(),
                source == null ? null : source.getPhysicalSchema() + "." + source.getPhysicalTable(),
                "app_data." + targetTable, details.sourceRecordCount(), analysis.compatible(), analysis.addedFields(),
                analysis.modifiedFields(), analysis.removedFields(), analysis.changes(), analysis.blockers());
    }

    private void copyData(MigrationDetails details, String targetSchema, String targetTable) {
        if (details.sourceVersion() == null || details.sourceRecordCount() == 0) return;
        List<String> targetColumns = new ArrayList<>(List.of("id", "source_document_id"));
        List<String> sourceExpressions = new ArrayList<>(List.of("id", "source_document_id"));
        for (FieldDefinition target : details.targetFields()) {
            FieldDefinition source = matchingSourceField(details.sourceFields(), target);
            if (source != null) {
                targetColumns.add(identifiers.requireValid(target.getColumnName(), "Cột đích"));
                String sourceColumn = identifiers.requireValid(source.getColumnName(), "Cột nguồn");
                sourceExpressions.add(shouldBackfillDefault(source, target)
                        ? "COALESCE(" + sourceColumn + ", " + defaultLiteral(target) + ")" : sourceColumn);
            } else if (migrationPlanner.hasDefault(target)) {
                targetColumns.add(identifiers.requireValid(target.getColumnName(), "Cột đích"));
                sourceExpressions.add(defaultLiteral(target));
            }
        }
        List<String> systemColumns = List.of("record_status", "created_at", "created_by", "updated_at", "updated_by", "row_version", "deleted");
        targetColumns.addAll(systemColumns);
        sourceExpressions.addAll(systemColumns);
        String sourceSchema = identifiers.requireValid(details.sourceVersion().getPhysicalSchema(), "Schema nguồn");
        String sourceTable = identifiers.requireValid(details.sourceVersion().getPhysicalTable(), "Bảng nguồn");
        String sql = "INSERT INTO " + targetSchema + "." + targetTable + " (" + String.join(",", targetColumns) + ") SELECT "
                + String.join(",", sourceExpressions) + " FROM " + sourceSchema + "." + sourceTable;
        int copied = jdbc.update(sql);
        if (copied != details.sourceRecordCount()) {
            throw ApiException.conflict("Số hồ sơ chuyển sang bảng mới không khớp dữ liệu cũ. Quá trình đã được dừng để bảo vệ dữ liệu.");
        }
    }

    private String defaultLiteral(FieldDefinition field) {
        if (!migrationPlanner.validDefault(field)) throw ApiException.badRequest("Giá trị điền sẵn không phù hợp với trường " + field.getLabel());
        String value = field.getDefaultValue().trim();
        return switch (field.getDataType()) {
            case INTEGER, DECIMAL -> value;
            case BOOLEAN -> value.toLowerCase(Locale.ROOT);
            case DATE -> "DATE '" + escaped(value) + "'";
            case DATETIME -> "TIMESTAMPTZ '" + escaped(value) + "'";
            case UUID -> "UUID '" + escaped(value) + "'";
            default -> "'" + escaped(value) + "'";
        };
    }

    private String escaped(String value) {
        return value.replace("'", "''");
    }

    private boolean shouldBackfillDefault(FieldDefinition source, FieldDefinition target) {
        return !Boolean.TRUE.equals(source.getRequired())
                && Boolean.TRUE.equals(target.getRequired())
                && migrationPlanner.hasDefault(target);
    }

    private FieldDefinition matchingSourceField(List<FieldDefinition> sourceFields, FieldDefinition target) {
        return sourceFields.stream()
                .filter(source -> source.getFieldCode().equalsIgnoreCase(target.getFieldCode()))
                .findFirst()
                .or(() -> sourceFields.stream()
                        .filter(source -> source.getColumnName().equalsIgnoreCase(target.getColumnName()))
                        .findFirst())
                .orElse(null);
    }

    private String physicalTableName(TemplateVersion version) {
        String code = identifiers.normalize(version.getTemplate().getCode());
        String suffix = "_v" + version.getVersionNo();
        int maxCodeLength = 63 - "dyn_".length() - suffix.length();
        return identifiers.requireValid("dyn_" + code.substring(0, Math.min(code.length(), maxCodeLength)) + suffix, "Tên bảng");
    }

    private String sqlType(FieldDefinition f) {
        return switch (f.getDataType()) {
            case STRING, EMAIL, PHONE -> "VARCHAR(" + f.getMaxLength() + ")";
            case TEXTAREA -> "TEXT";
            case INTEGER -> "BIGINT";
            case DECIMAL -> "NUMERIC(" + f.getPrecisionValue() + "," + f.getScaleValue() + ")";
            case DATE -> "DATE";
            case DATETIME -> "TIMESTAMPTZ";
            case BOOLEAN -> "BOOLEAN";
            case LIST -> "VARCHAR(255)";
            case UUID -> "UUID";
        };
    }

    private String columnDefinition(FieldDefinition field) {
        String definition = sqlType(field);
        if (migrationPlanner.hasDefault(field)) definition += " DEFAULT " + defaultLiteral(field);
        return definition;
    }

    private String configHash(UUID versionId) {
        List<FieldDefinition> fieldList = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId);
        List<ValidationRule> ruleList = rules.findByFieldIdIn(fieldList.stream().map(FieldDefinition::getId).toList());
        return configurationHasher.hash(fieldList, ruleList, templateRules.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId));
    }

    @SuppressWarnings("unused")
    private String legacyConfigHash(UUID versionId) {
        try {
            List<FieldDefinition> list = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId);
            Map<UUID, List<ValidationRule>> rulesByField = rules.findByFieldIdIn(list.stream().map(FieldDefinition::getId).toList())
                    .stream().collect(java.util.stream.Collectors.groupingBy(rule -> rule.getField().getId()));
            List<Map<String, Object>> canonical = list.stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fieldCode", f.getFieldCode()); m.put("columnName", f.getColumnName()); m.put("type", f.getDataType());
                m.put("length", f.getMaxLength()); m.put("precision", f.getPrecisionValue()); m.put("scale", f.getScaleValue());
                m.put("component", f.getComponentType()); m.put("required", f.getRequired());
                m.put("unique", f.getUniqueValue()); m.put("businessKey", f.getBusinessKey()); m.put("indexed", f.getIndexed());
                m.put("searchable", f.getSearchable()); m.put("sortable", f.getSortable()); m.put("exportable", f.getExportable());
                m.put("default", f.getDefaultValue()); m.put("readOnly", f.getReadOnly()); m.put("hidden", f.getHidden());
                m.put("lookup", f.getLookupSource() == null ? null : f.getLookupSource().getId());
                m.put("rules", rulesByField.getOrDefault(f.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(ValidationRule::getDisplayOrder))
                        .map(rule -> Map.of(
                                "type", rule.getRuleType().name(),
                                "config", rule.getRuleConfig(),
                                "message", rule.getErrorMessage() == null ? "" : rule.getErrorMessage(),
                                "order", rule.getDisplayOrder()))
                        .toList());
                return m;
            }).toList();
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(canonical));
            return HexFormat.of().formatHex(bytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không thể tính config hash", e);
        }
    }

    private String indexName(String table, String column, String prefix) {
        String suffix = Integer.toUnsignedString((table + column + prefix).hashCode(), 36);
        String ending = "_" + suffix;
        String base = prefix + "_" + table + "_" + column;
        return base.substring(0, Math.min(base.length(), 63 - ending.length())) + ending;
    }
    private String constraintName(String table, String column) {
        return indexName(table, column, "ck");
    }
    private record Plan(String schema, String table, List<String> statements) {}
    private record MigrationDetails(TemplateVersion sourceVersion, List<FieldDefinition> sourceFields,
                                    List<FieldDefinition> targetFields, long sourceRecordCount,
                                    MigrationPlanner.Analysis analysis) {}
}
