package com.example.dynamicform.schema;

import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.dto.TemplateDtos.ValidationIssue;
import com.example.dynamicform.template.dto.TemplateDtos.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class MetadataValidator {
    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "id", "source_document_id", "record_status", "created_at", "created_by",
            "updated_at", "updated_by", "row_version", "deleted");
    private static final Set<FieldDataType> BUSINESS_KEY_TYPES = Set.of(
            FieldDataType.STRING, FieldDataType.INTEGER, FieldDataType.UUID,
            FieldDataType.EMAIL, FieldDataType.PHONE);
    private static final Set<FieldDataType> TEXT_TYPES = Set.of(
            FieldDataType.STRING, FieldDataType.TEXTAREA, FieldDataType.EMAIL,
            FieldDataType.PHONE, FieldDataType.LIST);
    private static final Set<FieldDataType> INDEX_RECOMMENDED_TYPES = Set.of(
            FieldDataType.INTEGER, FieldDataType.DECIMAL, FieldDataType.DATE,
            FieldDataType.DATETIME, FieldDataType.LIST, FieldDataType.UUID);

    public ValidationResult validate(List<FieldDefinition> fields, List<ValidationRule> rules) {
        return validate(fields, rules, List.of());
    }

    public ValidationResult validate(List<FieldDefinition> fields, List<ValidationRule> rules, List<TemplateRule> formRules) {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        if (fields.isEmpty()) {
            errors.add(issue("ERROR", "NO_FIELDS", null, "Bieu mau can co it nhat mot truong du lieu."));
            return new ValidationResult(false, errors, warnings);
        }

        validateDuplicates(fields, errors);
        List<FieldDefinition> businessKeys = fields.stream().filter(f -> Boolean.TRUE.equals(f.getBusinessKey())).toList();
        if (businessKeys.isEmpty()) {
            errors.add(issue("ERROR", "BUSINESS_KEY_MISSING", null,
                    "Hay chon mot truong lam ma dinh danh chinh cho ho so."));
        } else if (businessKeys.size() > 1) {
            errors.add(issue("ERROR", "BUSINESS_KEY_MULTIPLE", null,
                    "Moi bieu mau chi duoc co mot ma dinh danh chinh."));
        }

        Map<UUID, List<ValidationRule>> rulesByField = new HashMap<>();
        rules.forEach(rule -> rulesByField.computeIfAbsent(rule.getField().getId(), key -> new ArrayList<>()).add(rule));
        for (FieldDefinition field : fields) {
            validateField(field, rulesByField.getOrDefault(field.getId(), List.of()), errors, warnings);
        }
        Set<String> codes = fields.stream().map(FieldDefinition::getFieldCode).collect(java.util.stream.Collectors.toSet());
        for (TemplateRule formRule : formRules) {
            if (formRule.getRuleType() != FormRuleType.AT_LEAST_ONE_FILLED) continue;
            Object configured = formRule.getRuleConfig().get("fieldCodes");
            if (!(configured instanceof Collection<?> values) || values.size() < 2
                    || values.stream().anyMatch(value -> !codes.contains(String.valueOf(value)))) {
                errors.add(issue("ERROR", "INVALID_FORM_RULE", null, "Dieu kien lien truong dang tham chieu truong khong ton tai."));
            }
        }
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void validateDuplicates(List<FieldDefinition> fields, List<ValidationIssue> errors) {
        Set<String> codes = new HashSet<>();
        Set<String> columns = new HashSet<>();
        for (FieldDefinition field : fields) {
            String code = field.getFieldCode().toLowerCase(Locale.ROOT);
            String column = field.getColumnName().toLowerCase(Locale.ROOT);
            if (!codes.add(code)) errors.add(issue("ERROR", "DUPLICATE_FIELD_CODE", field.getFieldCode(), "Ma truong dang bi trung."));
            if (!columns.add(column)) errors.add(issue("ERROR", "DUPLICATE_COLUMN", field.getFieldCode(), "Ten cot luu tru dang bi trung."));
        }
    }

    private void validateField(FieldDefinition field, List<ValidationRule> rules,
                               List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        String code = field.getFieldCode();
        if (SYSTEM_COLUMNS.contains(field.getColumnName())) {
            errors.add(issue("ERROR", "SYSTEM_COLUMN", code, "Ten cot luu tru dang trung voi cot co san cua he thong."));
        }
        if ((field.getDataType() == FieldDataType.STRING || field.getDataType() == FieldDataType.EMAIL || field.getDataType() == FieldDataType.PHONE)
                && (field.getMaxLength() == null || field.getMaxLength() < 1)) {
            errors.add(issue("ERROR", "MISSING_MAX_LENGTH", code, "Hay nhap so ky tu toi da cho truong nay."));
        }
        if (field.getDataType() == FieldDataType.DECIMAL
                && (field.getPrecisionValue() == null || field.getScaleValue() == null
                || field.getPrecisionValue() <= field.getScaleValue())) {
            errors.add(issue("ERROR", "INVALID_DECIMAL", code, "Tong so chu so phai lon hon so chu so sau dau phay."));
        }
        if (field.getDataType() == FieldDataType.LIST && field.getLookupSource() == null) {
            errors.add(issue("ERROR", "MISSING_LOOKUP", code, "Truong dang danh sach can chon mot danh muc du lieu."));
        }
        if (field.getDataType() == FieldDataType.LIST && field.getLookupSource() != null
                && !"ACTIVE".equalsIgnoreCase(field.getLookupSource().getStatus())) {
            errors.add(issue("ERROR", "LOOKUP_INACTIVE", code, "Danh muc dang tam ngung hoat dong."));
        }
        if (field.getDataType() != FieldDataType.LIST && field.getLookupSource() != null) {
            warnings.add(issue("WARNING", "UNUSED_LOOKUP", code, "Danh muc lua chon khong con phu hop voi loai du lieu."));
        }
        if ((Boolean.TRUE.equals(field.getSortable())
                || (Boolean.TRUE.equals(field.getSearchable()) && INDEX_RECOMMENDED_TYPES.contains(field.getDataType())))
                && !Boolean.TRUE.equals(field.getIndexed()) && !Boolean.TRUE.equals(field.getUniqueValue())) {
            warnings.add(issue("WARNING", "SEARCH_WITHOUT_INDEX", code, "Truong thuong dung de loc hoac sap xep nhung chua co chi muc."));
        }
        if (!compatible(field.getDataType(), field.getComponentType())) {
            errors.add(issue("ERROR", "INCOMPATIBLE_COMPONENT", code, "Kieu o nhap khong phu hop voi loai du lieu da chon."));
        }
        if (Boolean.TRUE.equals(field.getBusinessKey())) validateBusinessKey(field, errors);
        if (Boolean.TRUE.equals(field.getRequired()) && (Boolean.TRUE.equals(field.getHidden()) || Boolean.TRUE.equals(field.getReadOnly()))
                && !hasDefault(field)) {
            errors.add(issue("ERROR", "REQUIRED_NOT_EDITABLE", code,
                    "Truong bat buoc nhung nguoi dung khong the nhap va chua co gia tri dien san."));
        }
        if (!validDefault(field)) {
            errors.add(issue("ERROR", "INVALID_DEFAULT", code, "Gia tri dien san khong phu hop voi loai du lieu."));
        }
        validateRules(field, rules, errors);
    }

    private void validateBusinessKey(FieldDefinition field, List<ValidationIssue> errors) {
        String code = field.getFieldCode();
        if (!BUSINESS_KEY_TYPES.contains(field.getDataType())) {
            errors.add(issue("ERROR", "BUSINESS_KEY_INVALID_TYPE", code,
                    "Ma dinh danh chinh chi nen la chuoi ngan, so nguyen, UUID, email hoac so dien thoai."));
        }
        if (!Boolean.TRUE.equals(field.getRequired()) || !Boolean.TRUE.equals(field.getUniqueValue())
                || !Boolean.TRUE.equals(field.getIndexed()) || !Boolean.TRUE.equals(field.getSearchable())) {
            errors.add(issue("ERROR", "BUSINESS_KEY_INCONSISTENT", code,
                    "Ma dinh danh chinh phai bat buoc, khong trung, co chi muc va cho phep tim kiem."));
        }
    }

    private void validateRules(FieldDefinition field, List<ValidationRule> rules, List<ValidationIssue> errors) {
        BigDecimal minValue = null;
        BigDecimal maxValue = null;
        Integer minLength = null;
        Integer maxLength = null;
        for (ValidationRule rule : rules) {
            if (!ruleCompatible(field.getDataType(), rule.getRuleType())) {
                errors.add(issue("ERROR", "RULE_TYPE_MISMATCH", field.getFieldCode(),
                        "Dieu kien " + rule.getRuleType() + " khong phu hop voi loai du lieu."));
                continue;
            }
            try {
                switch (rule.getRuleType()) {
                    case MIN_LENGTH -> minLength = nonNegativeInteger(rule, "value");
                    case MAX_LENGTH -> maxLength = nonNegativeInteger(rule, "value");
                    case MIN_VALUE -> minValue = decimal(rule, "value");
                    case MAX_VALUE -> maxValue = decimal(rule, "value");
                    case REGEX -> {
                        try {
                            validateRegex(rule);
                        } catch (PatternSyntaxException ex) {
                            errors.add(issue("ERROR", "INVALID_REGEX", field.getFieldCode(),
                                    "Bieu thuc kiem tra khong dung cu phap."));
                        }
                    }
                    case DATE_RANGE -> validateDateRange(rule);
                    case DECIMAL_SCALE -> nonNegativeInteger(rule, "scale");
                    case REQUIRED, EMAIL -> { }
                }
            } catch (RuntimeException ex) {
                errors.add(issue("ERROR", "INVALID_RULE_CONFIG", field.getFieldCode(),
                        "Dieu kien " + rule.getRuleType() + " dang thieu hoac co gia tri khong hop le."));
            }
        }
        if (minLength != null && maxLength != null && minLength > maxLength) {
            errors.add(issue("ERROR", "INVALID_RULE_RANGE", field.getFieldCode(), "Do dai toi thieu khong duoc lon hon do dai toi da."));
        }
        if (minValue != null && maxValue != null && minValue.compareTo(maxValue) > 0) {
            errors.add(issue("ERROR", "INVALID_RULE_RANGE", field.getFieldCode(), "Gia tri toi thieu khong duoc lon hon gia tri toi da."));
        }
    }

    private boolean ruleCompatible(FieldDataType type, RuleType rule) {
        return switch (rule) {
            case REQUIRED -> true;
            case MIN_LENGTH, MAX_LENGTH, REGEX -> TEXT_TYPES.contains(type);
            case EMAIL -> type == FieldDataType.EMAIL || type == FieldDataType.STRING;
            case MIN_VALUE, MAX_VALUE -> type == FieldDataType.INTEGER || type == FieldDataType.DECIMAL;
            case DATE_RANGE -> type == FieldDataType.DATE;
            case DECIMAL_SCALE -> type == FieldDataType.DECIMAL;
        };
    }

    private int nonNegativeInteger(ValidationRule rule, String key) {
        Object value = rule.getRuleConfig().get(key);
        if (value == null) throw new IllegalArgumentException();
        int parsed = Integer.parseInt(value.toString());
        if (parsed < 0) throw new IllegalArgumentException();
        return parsed;
    }

    private BigDecimal decimal(ValidationRule rule, String key) {
        Object value = rule.getRuleConfig().get(key);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException();
        return new BigDecimal(value.toString());
    }

    private void validateRegex(ValidationRule rule) {
        Object value = rule.getRuleConfig().get("pattern");
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException();
        Pattern.compile(value.toString());
    }

    private void validateDateRange(ValidationRule rule) {
        Object minValue = rule.getRuleConfig().get("min");
        Object maxValue = rule.getRuleConfig().get("max");
        if (minValue == null && maxValue == null) throw new IllegalArgumentException();
        LocalDate min = minValue == null ? null : LocalDate.parse(minValue.toString());
        LocalDate max = maxValue == null ? null : LocalDate.parse(maxValue.toString());
        if (min != null && max != null && min.isAfter(max)) throw new IllegalArgumentException();
    }

    private boolean validDefault(FieldDefinition field) {
        if (!hasDefault(field)) return true;
        String value = field.getDefaultValue().trim();
        try {
            switch (field.getDataType()) {
                case INTEGER -> Long.parseLong(value);
                case DECIMAL -> {
                    BigDecimal decimal = new BigDecimal(value);
                    if (field.getPrecisionValue() != null && decimal.precision() > field.getPrecisionValue()) return false;
                    if (field.getScaleValue() != null && decimal.scale() > field.getScaleValue()) return false;
                }
                case DATE -> LocalDate.parse(value);
                case DATETIME -> parseDateTime(value);
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) return false;
                }
                case UUID -> UUID.fromString(value);
                case STRING, EMAIL, PHONE -> {
                    if (field.getMaxLength() != null && value.length() > field.getMaxLength()) return false;
                }
                case LIST -> {
                    if (field.getLookupSource() == null) return false;
                }
                case TEXTAREA -> { }
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean hasDefault(FieldDefinition field) {
        return field.getDefaultValue() != null && !field.getDefaultValue().isBlank();
    }

    private void parseDateTime(String value) {
        try {
            OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            LocalDateTime.parse(value);
        }
    }

    private boolean compatible(FieldDataType type, ComponentType component) {
        return switch (type) {
            case STRING -> Set.of(ComponentType.TEXT, ComponentType.TEXTAREA, ComponentType.HIDDEN).contains(component);
            case TEXTAREA -> component == ComponentType.TEXTAREA;
            case INTEGER, DECIMAL -> component == ComponentType.NUMBER;
            case DATE -> component == ComponentType.DATE;
            case DATETIME -> component == ComponentType.DATETIME;
            case BOOLEAN -> component == ComponentType.CHECKBOX || component == ComponentType.SWITCH;
            case LIST -> component == ComponentType.SELECT || component == ComponentType.AUTOCOMPLETE;
            case EMAIL -> component == ComponentType.EMAIL || component == ComponentType.TEXT;
            case PHONE -> component == ComponentType.PHONE || component == ComponentType.TEXT;
            case UUID -> component == ComponentType.HIDDEN || component == ComponentType.TEXT;
        };
    }

    private ValidationIssue issue(String level, String code, String field, String message) {
        return new ValidationIssue(level, code, field, message);
    }
}
