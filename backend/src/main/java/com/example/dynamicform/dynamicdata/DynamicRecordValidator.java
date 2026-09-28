package com.example.dynamicform.dynamicdata;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.lookup.service.LookupService;
import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.repository.TemplateRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DynamicRecordValidator {
    private final LookupService lookupService;
    private final TemplateRuleRepository templateRules;

    public Map<String, Object> validateAndConvert(Map<String, Object> payload, List<FieldDefinition> fields,
                                                   List<ValidationRule> rules, boolean completing) {
        return validateAndConvert(payload, fields, rules, completing, true);
    }

    public Map<String, Object> validateAndConvert(Map<String, Object> payload, List<FieldDefinition> fields,
                                                   List<ValidationRule> rules, boolean completing, boolean enforceReadOnly) {
        return validateAndConvert(payload, fields, rules, completing, enforceReadOnly, null);
    }

    public Map<String, Object> validateAndConvert(Map<String, Object> payload, List<FieldDefinition> fields,
                                                   List<ValidationRule> rules, boolean completing,
                                                   boolean enforceReadOnly, Set<String> lookupFieldsToValidate) {
        Map<String, FieldDefinition> byCode = new HashMap<>();
        fields.forEach(f -> byCode.put(f.getFieldCode(), f));
        for (String supplied : payload.keySet()) {
            if (!byCode.containsKey(supplied)) throw ApiException.badRequest("Trường dữ liệu không tồn tại trong cấu hình bảng: " + supplied);
            if (enforceReadOnly && Boolean.TRUE.equals(byCode.get(supplied).getReadOnly())) throw ApiException.badRequest("Trường chỉ được xem, không thể chỉnh sửa: " + byCode.get(supplied).getLabel());
        }
        Map<UUID, List<ValidationRule>> byField = new HashMap<>();
        rules.forEach(r -> byField.computeIfAbsent(r.getField().getId(), key -> new ArrayList<>()).add(r));
        Map<String, Object> converted = new LinkedHashMap<>();
        for (FieldDefinition field : fields) {
            Object raw = payload.containsKey(field.getFieldCode()) ? payload.get(field.getFieldCode()) : field.getDefaultValue();
            boolean empty = raw == null || (raw instanceof String s && s.isBlank());
            if (completing && Boolean.TRUE.equals(field.getRequired()) && empty) {
                throw ApiException.badRequest(field.getLabel() + " là bắt buộc");
            }
            if (empty) { converted.put(field.getFieldCode(), null); continue; }
            Object value = convert(field, raw);
            validateRules(field, value, byField.getOrDefault(field.getId(), List.of()));
            if (field.getLookupSource() != null
                    && (lookupFieldsToValidate == null || lookupFieldsToValidate.contains(field.getFieldCode()))
                    && !lookupService.valueExists(field.getLookupSource(), value)) {
                throw ApiException.badRequest("Giá trị danh mục không hợp lệ cho " + field.getLabel());
            }
            converted.put(field.getFieldCode(), value);
        }
        if (completing) validateFormRules(fields, converted);
        return converted;
    }

    private void validateFormRules(List<FieldDefinition> fields, Map<String, Object> values) {
        if (fields.isEmpty() || fields.getFirst().getTemplateVersion() == null) return;
        for (TemplateRule rule : templateRules.findByTemplateVersionIdOrderByDisplayOrderAsc(fields.getFirst().getTemplateVersion().getId())) {
            if (rule.getRuleType() != FormRuleType.AT_LEAST_ONE_FILLED) continue;
            Object configured = rule.getRuleConfig().get("fieldCodes");
            if (!(configured instanceof Collection<?> codes)) continue;
            boolean anyFilled = codes.stream().map(String::valueOf)
                    .map(values::get)
                    .anyMatch(value -> value != null && !(value instanceof String s && s.isBlank()));
            if (!anyFilled) throw ApiException.badRequest(rule.getErrorMessage() == null
                    ? "Cần nhập ít nhất một trong các trường liên quan." : rule.getErrorMessage());
        }
    }

    private Object convert(FieldDefinition f, Object raw) {
        try {
            return switch (f.getDataType()) {
                case STRING, TEXTAREA, LIST, EMAIL, PHONE -> checkedString(f, raw);
                case INTEGER -> raw instanceof Number n ? n.longValue() : Long.valueOf(raw.toString());
                case DECIMAL -> new BigDecimal(raw.toString());
                case DATE -> raw instanceof LocalDate d ? d : parseDate(raw.toString());
                case DATETIME -> raw instanceof OffsetDateTime d ? d : OffsetDateTime.parse(raw.toString());
                case BOOLEAN -> raw instanceof Boolean b ? b : parseBoolean(raw.toString());
                case UUID -> raw instanceof UUID u ? u : java.util.UUID.fromString(raw.toString());
            };
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw ApiException.badRequest("Giá trị của trường '" + f.getLabel() + "' không đúng định dạng " + dataTypeLabel(f.getDataType()) + ".");
        }
    }

    private String checkedString(FieldDefinition f, Object raw) {
        String value = raw.toString();
        if (f.getMaxLength() != null && value.length() > f.getMaxLength()) throw ApiException.badRequest(f.getLabel() + " vượt quá độ dài cho phép");
        if (f.getDataType() == FieldDataType.EMAIL && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest("Email không hợp lệ");
        return value;
    }

    private Boolean parseBoolean(String value) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
        return Boolean.valueOf(value);
    }

    private void validateRules(FieldDefinition f, Object value, List<ValidationRule> rules) {
        for (ValidationRule rule : rules) {
            Map<String, Object> c = rule.getRuleConfig();
            boolean ok;
            try {
                ok = switch (rule.getRuleType()) {
                    case REQUIRED -> value != null && !value.toString().isBlank();
                    case MIN_LENGTH -> value.toString().length() >= intConfig(c, "value");
                    case MAX_LENGTH -> value.toString().length() <= intConfig(c, "value");
                    case MIN_VALUE -> new BigDecimal(value.toString()).compareTo(decimalConfig(c, "value")) >= 0;
                    case MAX_VALUE -> new BigDecimal(value.toString()).compareTo(decimalConfig(c, "value")) <= 0;
                    case REGEX -> Pattern.compile(String.valueOf(c.get("pattern"))).matcher(value.toString()).matches();
                    case EMAIL -> value.toString().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
                    case DATE_RANGE -> dateRange(value, c);
                    case DECIMAL_SCALE -> new BigDecimal(value.toString()).scale() <= intConfig(c, "scale");
                };
            } catch (RuntimeException ex) {
                throw ApiException.badRequest("Cấu hình điều kiện nhập không hợp lệ cho trường: " + f.getLabel());
            }
            if (!ok) throw ApiException.badRequest(rule.getErrorMessage() == null ? "Giá trị không hợp lệ: " + f.getLabel() : rule.getErrorMessage());
        }
    }

    private boolean dateRange(Object value, Map<String, Object> c) {
        LocalDate date = value instanceof LocalDate d ? d : parseDate(value.toString());
        if (c.get("min") != null && date.isBefore(parseDate(c.get("min").toString()))) return false;
        return c.get("max") == null || !date.isAfter(parseDate(c.get("max").toString()));
    }
    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ignored) { return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd-MM-yyyy")); }
    }
    private int intConfig(Map<String, Object> c, String key) { return Integer.parseInt(String.valueOf(c.get(key))); }
    private BigDecimal decimalConfig(Map<String, Object> c, String key) { return new BigDecimal(String.valueOf(c.get(key))); }

    private String dataTypeLabel(FieldDataType type) {
        return switch (type) {
            case STRING, TEXTAREA -> "văn bản";
            case INTEGER -> "số nguyên";
            case DECIMAL -> "số";
            case DATE -> "ngày";
            case DATETIME -> "ngày và giờ";
            case BOOLEAN -> "có/không";
            case LIST -> "danh mục lựa chọn";
            case EMAIL -> "email";
            case PHONE -> "số điện thoại";
            case UUID -> "mã định danh";
        };
    }
}
