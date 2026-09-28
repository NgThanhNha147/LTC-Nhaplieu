package com.example.dynamicform.dynamicdata;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.lookup.service.LookupService;
import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.repository.TemplateRuleRepository;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DynamicRecordValidatorTest {
    private final DynamicRecordValidator validator = new DynamicRecordValidator(mock(LookupService.class), mock(TemplateRuleRepository.class));

    @Test
    void convertsValuesUsingMetadataTypes() {
        FieldDefinition age = field("age", FieldDataType.INTEGER);
        FieldDefinition active = field("active", FieldDataType.BOOLEAN);
        Map<String, Object> result = validator.validateAndConvert(Map.of("age", "42", "active", "true"), List.of(age, active), List.of(), false);
        assertThat(result.get("age")).isEqualTo(42L);
        assertThat(result.get("active")).isEqualTo(true);
    }

    @Test
    void enforcesRequiredWhenCompletingOnly() {
        FieldDefinition name = field("name", FieldDataType.STRING); name.setRequired(true); name.setMaxLength(20);
        assertThatCode(() -> validator.validateAndConvert(Map.of(), List.of(name), List.of(), false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateAndConvert(Map.of(), List.of(name), List.of(), true)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnknownField() {
        assertThatThrownBy(() -> validator.validateAndConvert(Map.of("sql", "drop"), List.of(), List.of(), false)).isInstanceOf(ApiException.class);
    }

    @Test
    void permitsExistingReadOnlyValueDuringInternalUpdateMerge() {
        FieldDefinition generated = field("generatedCode", FieldDataType.STRING); generated.setReadOnly(true); generated.setMaxLength(20);
        assertThatCode(() -> validator.validateAndConvert(Map.of("generatedCode", "AUTO-1"), List.of(generated), List.of(), false, false))
                .doesNotThrowAnyException();
    }

    private FieldDefinition field(String code, FieldDataType type) {
        FieldDefinition field = new FieldDefinition(); field.setId(UUID.randomUUID()); field.setFieldCode(code);
        field.setDataType(type); field.setReadOnly(false); field.setRequired(false); return field;
    }
}
