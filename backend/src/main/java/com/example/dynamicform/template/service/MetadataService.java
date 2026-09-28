package com.example.dynamicform.template.service;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.lookup.domain.LookupSource;
import com.example.dynamicform.lookup.repository.LookupSourceRepository;
import com.example.dynamicform.schema.IdentifierPolicy;
import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.dto.TemplateDtos.*;
import com.example.dynamicform.template.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService {
    private final TemplateService templateService;
    private final FieldGroupRepository groups;
    private final FieldDefinitionRepository fields;
    private final ValidationRuleRepository rules;
    private final TemplateRuleRepository templateRules;
    private final LookupSourceRepository lookups;
    private final IdentifierPolicy identifiers;

    @Transactional
    public GroupResponse createGroup(UUID versionId, GroupRequest request) {
        TemplateVersion version = templateService.requireVersion(versionId);
        templateService.requireMutable(version);
        if (request.repeatable()) throw ApiException.badRequest("Bản hiện tại chưa hỗ trợ nhóm thông tin lặp lại.");
        if (groups.existsByTemplateVersionIdAndCodeIgnoreCase(versionId, request.code())) throw ApiException.conflict("Tên nhóm thông tin đang bị trùng. Hãy sử dụng tên khác.");
        FieldGroup g = new FieldGroup();
        g.setId(UUID.randomUUID());
        g.setTemplateVersion(version);
        apply(g, request);
        groups.save(g);
        version.setStatus(VersionStatus.DRAFT);
        return map(g);
    }

    @Transactional
    public GroupResponse updateGroup(UUID id, GroupRequest request) {
        FieldGroup g = requireGroup(id);
        templateService.requireMutable(g.getTemplateVersion());
        if (request.repeatable()) throw ApiException.badRequest("Bản hiện tại chưa hỗ trợ nhóm thông tin lặp lại.");
        if (groups.existsByTemplateVersionIdAndCodeIgnoreCaseAndIdNot(g.getTemplateVersion().getId(), request.code(), id)) {
            throw ApiException.conflict("Tên nhóm thông tin đang bị trùng. Hãy sử dụng tên khác.");
        }
        apply(g, request);
        g.getTemplateVersion().setStatus(VersionStatus.DRAFT);
        return map(g);
    }

    @Transactional
    public void deleteGroup(UUID id) {
        FieldGroup g = requireGroup(id);
        templateService.requireMutable(g.getTemplateVersion());
        groups.delete(g);
        g.getTemplateVersion().setStatus(VersionStatus.DRAFT);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups(UUID versionId) {
        templateService.requireVersion(versionId);
        return groups.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId).stream().map(this::map).toList();
    }

    @Transactional
    public FieldResponse createField(UUID versionId, FieldRequest request) {
        TemplateVersion version = templateService.requireVersion(versionId);
        templateService.requireMutable(version);
        if (fields.existsByTemplateVersionIdAndFieldCodeIgnoreCase(versionId, request.fieldCode())) throw ApiException.conflict("Mã trường đã tồn tại trong bảng này. Hãy dùng một mã khác.");
        String column = request.columnName() == null || request.columnName().isBlank()
                ? identifiers.normalize(request.fieldCode()) : identifiers.requireValid(request.columnName(), "Tên cột");
        if (fields.existsByTemplateVersionIdAndColumnNameIgnoreCase(versionId, column)) throw ApiException.conflict("Tên cột lưu trữ đã tồn tại trong bảng này. Hãy đổi tên cột hoặc để hệ thống tự tạo.");
        FieldDefinition f = new FieldDefinition();
        f.setId(UUID.randomUUID());
        f.setTemplateVersion(version);
        selectBusinessKey(versionId, f.getId(), request.businessKey());
        apply(f, request, column);
        fields.save(f);
        replaceRules(f, request.validations());
        version.setStatus(VersionStatus.DRAFT);
        return map(f, rules.findByFieldIdOrderByDisplayOrderAsc(f.getId()));
    }

    @Transactional
    public FieldResponse updateField(UUID id, FieldRequest request) {
        FieldDefinition f = requireField(id);
        templateService.requireMutable(f.getTemplateVersion());
        UUID versionId = f.getTemplateVersion().getId();
        String column = request.columnName() == null || request.columnName().isBlank()
                ? f.getColumnName() : identifiers.requireValid(request.columnName(), "Tên cột");
        if (fields.existsByTemplateVersionIdAndFieldCodeIgnoreCaseAndIdNot(versionId, request.fieldCode(), id)) {
            throw ApiException.conflict("Mã trường đã tồn tại trong bảng này. Hãy dùng một mã khác.");
        }
        if (fields.existsByTemplateVersionIdAndColumnNameIgnoreCaseAndIdNot(versionId, column, id)) {
            throw ApiException.conflict("Tên cột lưu trữ đã tồn tại trong bảng này. Hãy đổi tên cột.");
        }
        selectBusinessKey(versionId, id, request.businessKey());
        apply(f, request, column);
        replaceRules(f, request.validations());
        f.getTemplateVersion().setStatus(VersionStatus.DRAFT);
        return map(f, rules.findByFieldIdOrderByDisplayOrderAsc(id));
    }

    @Transactional
    public void deleteField(UUID id) {
        FieldDefinition f = requireField(id);
        templateService.requireMutable(f.getTemplateVersion());
        fields.delete(f);
        f.getTemplateVersion().setStatus(VersionStatus.DRAFT);
    }

    @Transactional(readOnly = true)
    public List<FieldResponse> listFields(UUID versionId) {
        templateService.requireVersion(versionId);
        List<FieldDefinition> list = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId);
        Map<UUID, List<ValidationRule>> byField = rules.findByFieldIdIn(list.stream().map(FieldDefinition::getId).toList())
                .stream().collect(Collectors.groupingBy(r -> r.getField().getId()));
        return list.stream().map(f -> map(f, byField.getOrDefault(f.getId(), List.of()))).toList();
    }

    @Transactional
    public FormRuleResponse createFormRule(UUID versionId, FormRuleRequest request) {
        TemplateVersion version = templateService.requireVersion(versionId);
        templateService.requireMutable(version);
        validateFormRuleFields(versionId, request.fieldCodes());
        TemplateRule rule = new TemplateRule();
        rule.setId(UUID.randomUUID());
        rule.setTemplateVersion(version);
        apply(rule, request);
        templateRules.save(rule);
        version.setStatus(VersionStatus.DRAFT);
        return map(rule);
    }

    @Transactional
    public FormRuleResponse updateFormRule(UUID id, FormRuleRequest request) {
        TemplateRule rule = requireFormRule(id);
        templateService.requireMutable(rule.getTemplateVersion());
        validateFormRuleFields(rule.getTemplateVersion().getId(), request.fieldCodes());
        apply(rule, request);
        rule.getTemplateVersion().setStatus(VersionStatus.DRAFT);
        return map(rule);
    }

    @Transactional
    public void deleteFormRule(UUID id) {
        TemplateRule rule = requireFormRule(id);
        templateService.requireMutable(rule.getTemplateVersion());
        templateRules.delete(rule);
        rule.getTemplateVersion().setStatus(VersionStatus.DRAFT);
    }

    @Transactional(readOnly = true)
    public List<FormRuleResponse> listFormRules(UUID versionId) {
        templateService.requireVersion(versionId);
        return templateRules.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public FormMetadataResponse formMetadata(UUID versionId) {
        TemplateVersion version = templateService.requireVersion(versionId);
        List<FieldGroup> groupList = groups.findByTemplateVersionIdOrderByDisplayOrderAsc(version.getId());
        List<FieldResponse> fieldList = listFields(version.getId());
        Map<UUID, List<FieldResponse>> byGroup = fieldList.stream()
                .filter(f -> f.groupId() != null).collect(Collectors.groupingBy(FieldResponse::groupId));
        List<FormGroup> result = groupList.stream().map(g -> new FormGroup(g.getId(), g.getCode(), g.getLabel(),
                g.getDescription(), g.getDisplayOrder(), g.getColumnCount(), g.getCollapsible(),
                g.getDefaultCollapsed(), byGroup.getOrDefault(g.getId(), List.of()))).toList();
        List<FieldResponse> ungrouped = fieldList.stream().filter(f -> f.groupId() == null).toList();
        if (!ungrouped.isEmpty()) {
            List<FormGroup> withUngrouped = new ArrayList<>(result);
            withUngrouped.addFirst(new FormGroup(null, "GENERAL", "Thông tin chung", null, -1, 2, false, false, ungrouped));
            result = withUngrouped;
        }
        return new FormMetadataResponse(version.getTemplate().getCode(), version.getTemplate().getName(),
                version.getVersionNo(), version.getId(), result, listFormRules(versionId));
    }

    private void apply(TemplateRule rule, FormRuleRequest request) {
        rule.setRuleType(request.ruleType());
        rule.setRuleConfig(Map.of("fieldCodes", List.copyOf(request.fieldCodes())));
        rule.setErrorMessage(request.errorMessage());
        rule.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private void validateFormRuleFields(UUID versionId, List<String> fieldCodes) {
        Set<String> available = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId).stream()
                .map(FieldDefinition::getFieldCode)
                .collect(Collectors.toSet());
        List<String> missing = fieldCodes.stream().filter(code -> !available.contains(code)).toList();
        if (!missing.isEmpty()) throw ApiException.badRequest("Dieu kien dang tham chieu truong khong ton tai: " + String.join(", ", missing));
        if (new HashSet<>(fieldCodes).size() != fieldCodes.size()) throw ApiException.badRequest("Khong duoc chon trung truong trong cung mot dieu kien.");
    }

    private void apply(FieldGroup g, GroupRequest r) {
        g.setCode(r.code().toUpperCase()); g.setLabel(r.label()); g.setDescription(r.description());
        g.setDisplayOrder(r.displayOrder()); g.setColumnCount(r.columnCount()); g.setCollapsible(r.collapsible());
        g.setDefaultCollapsed(r.defaultCollapsed()); g.setRepeatable(r.repeatable());
    }

    private void apply(FieldDefinition f, FieldRequest r, String column) {
        f.setGroup(r.groupId() == null ? null : requireGroupForVersion(r.groupId(), f.getTemplateVersion().getId()));
        f.setFieldCode(r.fieldCode()); f.setColumnName(column); f.setLabel(r.label()); f.setDataType(r.dataType());
        f.setComponentType(r.componentType()); f.setDescription(r.description()); f.setPlaceholder(r.placeholder());
        boolean businessKey = r.businessKey();
        f.setBusinessKey(businessKey);
        f.setRequired(businessKey || r.required()); f.setUniqueValue(businessKey || r.uniqueValue()); f.setIndexed(businessKey || r.indexed());
        f.setSearchable(businessKey || r.searchable()); f.setSortable(r.sortable()); f.setExportable(r.exportable());
        f.setMaxLength(r.maxLength()); f.setPrecisionValue(r.precision()); f.setScaleValue(r.scale());
        f.setDefaultValue(r.defaultValue()); f.setDisplayOrder(r.displayOrder()); f.setGridSpan(r.gridSpan());
        f.setReadOnly(r.readOnly()); f.setHidden(r.hidden()); f.setHelpText(r.helpText());
        LookupSource lookup = null;
        if (r.lookupSourceId() != null) {
            lookup = lookups.findById(r.lookupSourceId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục lựa chọn đã cấu hình."));
        } else if (r.lookup() != null && r.lookup().lookupSourceId() != null) {
            lookup = lookups.findById(r.lookup().lookupSourceId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục lựa chọn đã cấu hình."));
        } else if (r.lookup() != null && r.lookup().lookupCode() != null && !r.lookup().lookupCode().isBlank()) {
            lookup = lookups.findByCodeIgnoreCase(r.lookup().lookupCode()).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục lựa chọn đã cấu hình."));
        }
        f.setLookupSource(lookup);
    }

    private void replaceRules(FieldDefinition field, List<RuleRequest> requests) {
        rules.deleteByFieldId(field.getId());
        if (requests == null) return;
        int order = 0;
        for (RuleRequest request : requests) {
            ValidationRule rule = new ValidationRule();
            rule.setId(UUID.randomUUID()); rule.setField(field); rule.setRuleType(request.ruleType());
            rule.setRuleConfig(request.ruleConfig() == null ? Map.of() : request.ruleConfig());
            rule.setErrorMessage(request.errorMessage());
            rule.setDisplayOrder(request.displayOrder() == null ? order++ : request.displayOrder());
            rules.save(rule);
        }
    }

    private void selectBusinessKey(UUID versionId, UUID selectedId, boolean businessKey) {
        if (!businessKey) return;
        fields.clearBusinessKeyExcept(versionId, selectedId);
    }

    private FieldGroup requireGroupForVersion(UUID groupId, UUID versionId) {
        FieldGroup group = requireGroup(groupId);
        if (!group.getTemplateVersion().getId().equals(versionId)) throw ApiException.badRequest("Nhóm trường không thuộc phiên bản bảng đang cấu hình.");
        return group;
    }

    private FieldGroup requireGroup(UUID id) { return groups.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhóm thông tin cần chỉnh sửa.")); }
    private FieldDefinition requireField(UUID id) { return fields.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy trường dữ liệu cần chỉnh sửa.")); }
    private TemplateRule requireFormRule(UUID id) {
        return templateRules.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay dieu kien hoan tat ho so."));
    }

    private GroupResponse map(FieldGroup g) { return new GroupResponse(g.getId(), g.getCode(), g.getLabel(), g.getDescription(), g.getDisplayOrder(), g.getColumnCount(), g.getCollapsible(), g.getDefaultCollapsed(), g.getRepeatable()); }
    public FieldResponse map(FieldDefinition f, List<ValidationRule> ruleList) {
        List<RuleResponse> mapped = ruleList.stream().sorted(Comparator.comparing(ValidationRule::getDisplayOrder))
                .map(r -> new RuleResponse(r.getId(), r.getRuleType(), r.getRuleConfig(), r.getErrorMessage(), r.getDisplayOrder())).toList();
        return new FieldResponse(f.getId(), f.getGroup() == null ? null : f.getGroup().getId(), f.getFieldCode(), f.getColumnName(),
                f.getLabel(), f.getDataType(), f.getComponentType(), f.getDescription(), f.getPlaceholder(), f.getRequired(),
                f.getUniqueValue(), f.getBusinessKey(), f.getIndexed(), f.getSearchable(), f.getSortable(), f.getExportable(), f.getMaxLength(),
                f.getPrecisionValue(), f.getScaleValue(), f.getDefaultValue(), f.getDisplayOrder(), f.getGridSpan(),
                f.getReadOnly(), f.getHidden(), f.getHelpText(), f.getLookupSource() == null ? null : f.getLookupSource().getId(),
                f.getLookupSource() == null ? null : f.getLookupSource().getCode(), f.getLookupSource() == null ? null
                : new LookupConfig(f.getLookupSource().getId(), f.getLookupSource().getCode(), "SINGLE", true,
                f.getComponentType() == ComponentType.AUTOCOMPLETE, 0, Map.of()), mapped);
    }

    private FormRuleResponse map(TemplateRule rule) {
        Object configured = rule.getRuleConfig().get("fieldCodes");
        List<String> fieldCodes = configured instanceof Collection<?> values
                ? values.stream().map(String::valueOf).toList() : List.of();
        return new FormRuleResponse(rule.getId(), rule.getRuleType(), fieldCodes,
                rule.getErrorMessage(), rule.getDisplayOrder());
    }
}
