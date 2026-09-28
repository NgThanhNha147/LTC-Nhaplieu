package com.example.dynamicform.schema;

import com.example.dynamicform.template.domain.ComponentType;
import com.example.dynamicform.template.domain.FieldDataType;
import com.example.dynamicform.template.domain.FieldDefinition;
import com.example.dynamicform.template.domain.RuleType;
import com.example.dynamicform.template.domain.ValidationRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationHasherTest {
    private final ConfigurationHasher hasher = new ConfigurationHasher(new ObjectMapper());

    @Test
    void producesSameHashRegardlessOfDatabaseRowAndMapOrder() {
        FieldDefinition owner = field("ownerName", "owner_name");
        FieldDefinition serial = field("serialNumber", "serial_number");
        ValidationRule firstRule = rule(owner, linkedMap("max", 100, "min", 2));
        ValidationRule sameRuleDifferentMapOrder = rule(owner, linkedMap("min", 2, "max", 100));

        String first = hasher.hash(List.of(owner, serial), List.of(firstRule));
        String second = hasher.hash(List.of(serial, owner), List.of(sameRuleDifferentMapOrder));

        assertThat(first).startsWith("v2:").isEqualTo(second);
    }

    @Test
    void changesHashWhenSchemaConfigurationChanges() {
        FieldDefinition original = field("ownerName", "owner_name");
        FieldDefinition changed = field("ownerName", "owner_name");
        changed.setMaxLength(500);

        assertThat(hasher.hash(List.of(original), List.of()))
                .isNotEqualTo(hasher.hash(List.of(changed), List.of()));
    }

    private FieldDefinition field(String code, String column) {
        FieldDefinition field = new FieldDefinition();
        field.setId(UUID.randomUUID());
        field.setFieldCode(code);
        field.setColumnName(column);
        field.setDataType(FieldDataType.STRING);
        field.setComponentType(ComponentType.TEXT);
        field.setRequired(false);
        field.setUniqueValue(false);
        field.setBusinessKey(false);
        field.setIndexed(false);
        field.setSearchable(true);
        field.setSortable(true);
        field.setExportable(true);
        field.setMaxLength(255);
        field.setDisplayOrder(1);
        field.setGridSpan(6);
        field.setReadOnly(false);
        field.setHidden(false);
        return field;
    }

    private ValidationRule rule(FieldDefinition field, Map<String, Object> config) {
        ValidationRule rule = new ValidationRule();
        rule.setId(UUID.randomUUID());
        rule.setField(field);
        rule.setRuleType(RuleType.MAX_LENGTH);
        rule.setRuleConfig(config);
        rule.setDisplayOrder(1);
        return rule;
    }

    private Map<String, Object> linkedMap(Object... entries) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            value.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return value;
    }
}
