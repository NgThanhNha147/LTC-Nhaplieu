package com.example.dynamicform.schema;

import com.example.dynamicform.template.domain.FieldDefinition;
import com.example.dynamicform.template.domain.ValidationRule;
import com.example.dynamicform.template.domain.TemplateRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ConfigurationHasher {
    private static final String HASH_VERSION = "v2:";
    private final ObjectMapper objectMapper;

    String hash(List<FieldDefinition> fields, List<ValidationRule> rules) {
        return hash(fields, rules, List.of());
    }

    String hash(List<FieldDefinition> fields, List<ValidationRule> rules, List<TemplateRule> formRules) {
        Map<UUID, List<ValidationRule>> rulesByField = rules.stream()
                .collect(Collectors.groupingBy(rule -> rule.getField().getId()));

        List<Map<String, Object>> canonical = fields.stream()
                .sorted(Comparator.comparing(FieldDefinition::getFieldCode, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(FieldDefinition::getColumnName, String.CASE_INSENSITIVE_ORDER))
                .map(field -> canonicalField(field, rulesByField.getOrDefault(field.getId(), List.of())))
                .toList();
        try {
            Map<String, Object> complete = new LinkedHashMap<>();
            complete.put("fields", canonical);
            complete.put("formRules", formRules.stream().map(this::canonicalFormRule).toList());
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(complete));
            return HASH_VERSION + HexFormat.of().formatHex(bytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate configuration hash", exception);
        }
    }

    private Map<String, Object> canonicalFormRule(TemplateRule rule) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", rule.getRuleType().name());
        value.put("config", canonicalValue(rule.getRuleConfig()));
        value.put("message", rule.getErrorMessage() == null ? "" : rule.getErrorMessage());
        value.put("order", rule.getDisplayOrder());
        return value;
    }

    boolean isLegacyHash(String hash) {
        return hash != null && (hash.matches("[0-9a-f]{64}") || hash.startsWith("v2:"));
    }

    private Map<String, Object> canonicalField(FieldDefinition field, List<ValidationRule> fieldRules) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fieldCode", field.getFieldCode());
        value.put("columnName", field.getColumnName());
        value.put("type", field.getDataType());
        value.put("length", field.getMaxLength());
        value.put("precision", field.getPrecisionValue());
        value.put("scale", field.getScaleValue());
        value.put("component", field.getComponentType());
        value.put("required", field.getRequired());
        value.put("unique", field.getUniqueValue());
        value.put("businessKey", field.getBusinessKey());
        value.put("indexed", field.getIndexed());
        value.put("searchable", field.getSearchable());
        value.put("sortable", field.getSortable());
        value.put("exportable", field.getExportable());
        value.put("default", field.getDefaultValue());
        value.put("readOnly", field.getReadOnly());
        value.put("hidden", field.getHidden());
        value.put("lookup", field.getLookupSource() == null ? null : field.getLookupSource().getId());
        value.put("rules", fieldRules.stream()
                .sorted(Comparator.comparing(ValidationRule::getDisplayOrder)
                        .thenComparing(rule -> rule.getRuleType().name())
                        .thenComparing(rule -> canonicalJson(rule.getRuleConfig())))
                .map(this::canonicalRule)
                .toList());
        return value;
    }

    private Map<String, Object> canonicalRule(ValidationRule rule) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", rule.getRuleType().name());
        value.put("config", canonicalValue(rule.getRuleConfig()));
        value.put("message", rule.getErrorMessage() == null ? "" : rule.getErrorMessage());
        value.put("order", rule.getDisplayOrder());
        return value;
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalValue(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot normalize configuration hash", exception);
        }
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalValue(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::canonicalValue).toList();
        }
        return value;
    }
}
