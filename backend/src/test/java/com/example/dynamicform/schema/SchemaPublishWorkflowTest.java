package com.example.dynamicform.schema;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.template.domain.FormTemplate;
import com.example.dynamicform.template.domain.TemplateVersion;
import com.example.dynamicform.template.domain.VersionStatus;
import com.example.dynamicform.template.repository.FieldDefinitionRepository;
import com.example.dynamicform.template.repository.TemplateRuleRepository;
import com.example.dynamicform.template.repository.TemplateVersionRepository;
import com.example.dynamicform.template.repository.ValidationRuleRepository;
import com.example.dynamicform.template.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaPublishWorkflowTest {
    @Mock TemplateVersionRepository versions;
    @Mock FieldDefinitionRepository fields;
    @Mock ValidationRuleRepository rules;
    @Mock TemplateRuleRepository templateRules;
    @Mock TemplateService templates;
    @Mock MetadataValidator validator;
    @Mock MigrationPlanner migrationPlanner;
    @Mock IdentifierPolicy identifiers;
    @Mock TemplateOperationLock operationLock;
    @Mock CurrentUser currentUser;
    @Mock ConfigurationHasher configurationHasher;
    @Mock JdbcTemplate jdbc;
    @Mock ObjectMapper objectMapper;
    @InjectMocks SchemaGeneratorService service;

    @Test
    void cannotPublishWhilePreviousVersionHasActiveBatches() {
        UUID targetId = UUID.randomUUID();
        FormTemplate template = new FormTemplate();
        template.setId(UUID.randomUUID());
        template.setCode("LAND_CERTIFICATE");

        TemplateVersion current = version(UUID.randomUUID(), template, 1, VersionStatus.PUBLISHED);
        TemplateVersion target = version(targetId, template, 2, VersionStatus.GENERATED);
        target.setConfigHash("current-hash");

        when(versions.findById(targetId)).thenReturn(Optional.of(target));
        when(versions.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(fields.findByTemplateVersionIdOrderByDisplayOrderAsc(targetId)).thenReturn(List.of());
        when(rules.findByFieldIdIn(List.of())).thenReturn(List.of());
        when(templateRules.findByTemplateVersionIdOrderByDisplayOrderAsc(targetId)).thenReturn(List.of());
        when(configurationHasher.hash(List.of(), List.of(), List.of())).thenReturn("current-hash");
        when(versions.findFirstByTemplateCodeIgnoreCaseAndStatusOrderByVersionNoDesc(
                "LAND_CERTIFICATE", VersionStatus.PUBLISHED)).thenReturn(Optional.of(current));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(current.getId()))).thenReturn(2L);

        assertThatThrownBy(() -> service.publish(targetId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2 đợt hồ sơ chưa lưu trữ");
    }

    private TemplateVersion version(UUID id, FormTemplate template, int number, VersionStatus status) {
        TemplateVersion version = new TemplateVersion();
        version.setId(id);
        version.setTemplate(template);
        version.setVersionNo(number);
        version.setStatus(status);
        version.setPhysicalSchema("app_data");
        version.setPhysicalTable("dyn_land_certificate_v" + number);
        return version;
    }
}

