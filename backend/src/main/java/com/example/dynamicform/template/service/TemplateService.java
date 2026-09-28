package com.example.dynamicform.template.service;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.dto.TemplateDtos.*;
import com.example.dynamicform.template.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateService {
    private final FormTemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final FieldGroupRepository groups;
    private final FieldDefinitionRepository fields;
    private final ValidationRuleRepository rules;
    private final TemplateRuleRepository templateRules;
    private final CurrentUser currentUser;

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        if (templates.existsByCodeIgnoreCase(request.code())) throw ApiException.conflict("Mã biểu mẫu đã tồn tại. Hãy sử dụng một mã khác.");
        Instant now = Instant.now();
        FormTemplate entity = new FormTemplate();
        entity.setId(UUID.randomUUID());
        entity.setCode(request.code().toUpperCase());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(TemplateStatus.DRAFT);
        entity.setCurrentVersion(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(currentUser.username());
        entity.setUpdatedBy(currentUser.username());
        templates.save(entity);

        TemplateVersion version = new TemplateVersion();
        version.setId(UUID.randomUUID());
        version.setTemplate(entity);
        version.setVersionNo(1);
        version.setStatus(VersionStatus.DRAFT);
        version.setCreatedAt(now);
        version.setCreatedBy(currentUser.username());
        versions.save(version);
        return map(entity);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return templates.findAll().stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID id) {
        return map(requireTemplate(id));
    }

    @Transactional
    public TemplateResponse update(UUID id, TemplateRequest request) {
        FormTemplate entity = requireTemplate(id);
        boolean codeChanged = !entity.getCode().equalsIgnoreCase(request.code());
        if (codeChanged && hasGeneratedVersion(id)) {
            throw ApiException.conflict("Không thể đổi mã biểu mẫu sau khi đã tạo bảng dữ liệu.");
        }
        if (!entity.getCode().equalsIgnoreCase(request.code()) && templates.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.conflict("Mã biểu mẫu đã tồn tại. Hãy sử dụng một mã khác.");
        }
        entity.setCode(request.code().toUpperCase());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(currentUser.username());
        return map(entity);
    }

    @Transactional
    public void delete(UUID id) {
        FormTemplate entity = requireTemplate(id);
        List<TemplateVersion> versionList = versions.findByTemplateIdOrderByVersionNoDesc(id);
        boolean generated = versionList.stream().anyMatch(this::isGenerated);
        if (generated) throw ApiException.conflict("Không thể xóa biểu mẫu đã tạo bảng dữ liệu. Hãy ngừng sử dụng biểu mẫu thay vì xóa.");
        versions.deleteAll(versionList);
        versions.flush();
        templates.delete(entity);
    }

    @Transactional
    public VersionResponse createVersion(UUID templateId) {
        FormTemplate template = requireTemplate(templateId);
        List<TemplateVersion> existing = versions.findByTemplateIdOrderByVersionNoDesc(templateId);
        TemplateVersion source = existing.isEmpty() ? null : existing.getFirst();
        int next = existing.stream().mapToInt(TemplateVersion::getVersionNo).max().orElse(0) + 1;
        TemplateVersion version = new TemplateVersion();
        version.setId(UUID.randomUUID());
        version.setTemplate(template);
        version.setVersionNo(next);
        version.setStatus(VersionStatus.DRAFT);
        version.setCreatedAt(Instant.now());
        version.setCreatedBy(currentUser.username());
        versions.save(version);
        if (source != null) cloneMetadata(source, version);
        return map(version);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> versions(UUID templateId) {
        requireTemplate(templateId);
        return versions.findByTemplateIdOrderByVersionNoDesc(templateId).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public VersionResponse getVersion(UUID templateId, Integer versionNo) {
        return map(versions.findByTemplateIdAndVersionNo(templateId, versionNo)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu.")));
    }

    private void cloneMetadata(TemplateVersion source, TemplateVersion target) {
        java.util.Map<UUID, FieldGroup> groupMap = new java.util.HashMap<>();
        for (FieldGroup old : groups.findByTemplateVersionIdOrderByDisplayOrderAsc(source.getId())) {
            FieldGroup copy = new FieldGroup(); copy.setId(UUID.randomUUID()); copy.setTemplateVersion(target);
            copy.setCode(old.getCode()); copy.setLabel(old.getLabel()); copy.setDescription(old.getDescription());
            copy.setDisplayOrder(old.getDisplayOrder()); copy.setColumnCount(old.getColumnCount()); copy.setCollapsible(old.getCollapsible());
            copy.setDefaultCollapsed(old.getDefaultCollapsed()); copy.setRepeatable(old.getRepeatable()); groups.save(copy); groupMap.put(old.getId(), copy);
        }
        for (FieldDefinition old : fields.findByTemplateVersionIdOrderByDisplayOrderAsc(source.getId())) {
            FieldDefinition copy = new FieldDefinition(); copy.setId(UUID.randomUUID()); copy.setTemplateVersion(target);
            copy.setGroup(old.getGroup() == null ? null : groupMap.get(old.getGroup().getId())); copy.setFieldCode(old.getFieldCode());
            copy.setColumnName(old.getColumnName()); copy.setLabel(old.getLabel()); copy.setDataType(old.getDataType());
            copy.setComponentType(old.getComponentType()); copy.setDescription(old.getDescription()); copy.setPlaceholder(old.getPlaceholder());
            copy.setRequired(old.getRequired()); copy.setUniqueValue(old.getUniqueValue()); copy.setBusinessKey(old.getBusinessKey()); copy.setIndexed(old.getIndexed());
            copy.setSearchable(old.getSearchable()); copy.setSortable(old.getSortable()); copy.setExportable(old.getExportable());
            copy.setMaxLength(old.getMaxLength()); copy.setPrecisionValue(old.getPrecisionValue()); copy.setScaleValue(old.getScaleValue());
            copy.setDefaultValue(old.getDefaultValue()); copy.setDisplayOrder(old.getDisplayOrder()); copy.setGridSpan(old.getGridSpan());
            copy.setReadOnly(old.getReadOnly()); copy.setHidden(old.getHidden()); copy.setHelpText(old.getHelpText()); copy.setLookupSource(old.getLookupSource());
            fields.save(copy);
            for (ValidationRule oldRule : rules.findByFieldIdOrderByDisplayOrderAsc(old.getId())) {
                ValidationRule rule = new ValidationRule(); rule.setId(UUID.randomUUID()); rule.setField(copy); rule.setRuleType(oldRule.getRuleType());
                rule.setRuleConfig(new java.util.LinkedHashMap<>(oldRule.getRuleConfig())); rule.setErrorMessage(oldRule.getErrorMessage());
                rule.setDisplayOrder(oldRule.getDisplayOrder()); rules.save(rule);
            }
        }
        for (TemplateRule old : templateRules.findByTemplateVersionIdOrderByDisplayOrderAsc(source.getId())) {
            TemplateRule copy = new TemplateRule();
            copy.setId(UUID.randomUUID());
            copy.setTemplateVersion(target);
            copy.setRuleType(old.getRuleType());
            copy.setRuleConfig(new java.util.LinkedHashMap<>(old.getRuleConfig()));
            copy.setErrorMessage(old.getErrorMessage());
            copy.setDisplayOrder(old.getDisplayOrder());
            templateRules.save(copy);
        }
    }

    public FormTemplate requireTemplate(UUID id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy biểu mẫu."));
    }

    public TemplateVersion requireVersion(UUID id) {
        return versions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
    }

    public void requireMutable(TemplateVersion version) {
        if (version.getStatus() != VersionStatus.DRAFT && version.getStatus() != VersionStatus.VALIDATED) {
            throw ApiException.conflict("Phiên bản này đã tạo bảng hoặc đang được sử dụng nên không thể sửa trực tiếp. Hãy tạo bản chỉnh sửa mới.");
        }
    }

    private boolean hasGeneratedVersion(UUID templateId) {
        return versions.findByTemplateIdOrderByVersionNoDesc(templateId).stream().anyMatch(this::isGenerated);
    }

    private boolean isGenerated(TemplateVersion version) {
        return version.getStatus() == VersionStatus.GENERATED || version.getStatus() == VersionStatus.PUBLISHED || version.getStatus() == VersionStatus.ARCHIVED;
    }

    private TemplateResponse map(FormTemplate t) {
        UUID currentVersionId = t.getCurrentVersion() == null ? null
                : versions.findByTemplateIdAndVersionNo(t.getId(), t.getCurrentVersion()).map(TemplateVersion::getId).orElse(null);
        return new TemplateResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.getStatus(),
                t.getCurrentVersion(), currentVersionId, t.getCreatedAt(), t.getUpdatedAt());
    }

    public VersionResponse map(TemplateVersion v) {
        return new VersionResponse(v.getId(), v.getTemplate().getId(), v.getVersionNo(), v.getStatus(),
                v.getPhysicalSchema(), v.getPhysicalTable(), v.getConfigHash(), v.getGeneratedAt(), v.getPublishedAt(),
                v.getStorageStatus(), v.getPurgedAt(), v.getPurgedBy(), v.getPurgeReason());
    }
}
