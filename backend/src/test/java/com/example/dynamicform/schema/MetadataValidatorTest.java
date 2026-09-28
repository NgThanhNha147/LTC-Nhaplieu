package com.example.dynamicform.schema;

import com.example.dynamicform.template.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataValidatorTest {
    private final MetadataValidator validator = new MetadataValidator();

    @Test
    void rejectsStringWithoutLengthAndListWithoutLookup() {
        FieldDefinition text = field("name", FieldDataType.STRING, ComponentType.TEXT);
        FieldDefinition list = field("type", FieldDataType.LIST, ComponentType.SELECT);
        var result = validator.validate(List.of(text, list), List.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(e -> e.code()).contains("MISSING_MAX_LENGTH", "MISSING_LOOKUP");
    }

    @Test
    void acceptsBasicNumberField() {
        FieldDefinition key = businessKey();
        FieldDefinition number = field("area", FieldDataType.DECIMAL, ComponentType.NUMBER);
        number.setPrecisionValue(18); number.setScaleValue(2);
        assertThat(validator.validate(List.of(key, number), List.of()).valid()).isTrue();
    }

    @Test
    void rejectsInvalidDateRangeAndDecimalScaleRules() {
        FieldDefinition date = field("issuedDate", FieldDataType.DATE, ComponentType.DATE);
        ValidationRule dateRange = rule(date, RuleType.DATE_RANGE, Map.of("min", "2026-12-31", "max", "2026-01-01"));
        ValidationRule scale = rule(date, RuleType.DECIMAL_SCALE, Map.of("scale", -1));

        var result = validator.validate(List.of(businessKey(), date), List.of(dateRange, scale));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(e -> e.code())
                .containsExactlyInAnyOrder("INVALID_RULE_CONFIG", "RULE_TYPE_MISMATCH");
    }

    @Test
    void rejectsMultipleBusinessKeysAndInconsistentKeyFlags() {
        FieldDefinition first = businessKey();
        FieldDefinition second = businessKey();
        second.setId(UUID.randomUUID());
        second.setFieldCode("secondaryCode");
        second.setColumnName("secondary_code");
        second.setIndexed(false);

        var result = validator.validate(List.of(first, second), List.of());

        assertThat(result.errors()).extracting(e -> e.code())
                .contains("BUSINESS_KEY_MULTIPLE", "BUSINESS_KEY_INCONSISTENT");
    }

    @Test
    void rejectsInvalidRegexAndDefaultValue() {
        FieldDefinition key = businessKey();
        FieldDefinition email = field("contactEmail", FieldDataType.EMAIL, ComponentType.EMAIL);
        email.setMaxLength(120);
        email.setDefaultValue("x".repeat(121));
        ValidationRule regex = rule(email, RuleType.REGEX, Map.of("pattern", "[invalid"));

        var result = validator.validate(List.of(key, email), List.of(regex));

        assertThat(result.errors()).extracting(e -> e.code())
                .contains("INVALID_REGEX", "INVALID_DEFAULT");
    }

    private FieldDefinition field(String code, FieldDataType type, ComponentType component) {
        FieldDefinition field = new FieldDefinition(); field.setId(UUID.randomUUID()); field.setFieldCode(code);
        field.setColumnName(code); field.setDataType(type); field.setComponentType(component);
        field.setRequired(false); field.setBusinessKey(false); field.setSearchable(false); field.setSortable(false);
        field.setIndexed(false); field.setUniqueValue(false); field.setReadOnly(false); field.setHidden(false);
        return field;
    }

    private FieldDefinition businessKey() {
        FieldDefinition field = field("recordCode", FieldDataType.STRING, ComponentType.TEXT);
        field.setColumnName("record_code");
        field.setMaxLength(100);
        field.setBusinessKey(true);
        field.setRequired(true);
        field.setUniqueValue(true);
        field.setIndexed(true);
        field.setSearchable(true);
        return field;
    }

    private ValidationRule rule(FieldDefinition field, RuleType type, Map<String, Object> config) {
        ValidationRule rule = new ValidationRule();
        rule.setId(UUID.randomUUID()); rule.setField(field); rule.setRuleType(type); rule.setRuleConfig(config);
        return rule;
    }
}
