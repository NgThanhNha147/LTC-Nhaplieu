package com.example.dynamicform.dynamicdata;

import com.example.dynamicform.audit.AuditService;
import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.SearchRequest;
import com.example.dynamicform.schema.TemplateOperationLock;
import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DynamicDataSearchTest {
    private final TemplateVersionRepository versions = mock(TemplateVersionRepository.class);
    private final FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
    private final ValidationRuleRepository rules = mock(ValidationRuleRepository.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private DynamicDataService service;

    @BeforeEach
    void setUp() {
        service = new DynamicDataService(versions, fields, rules, mock(DynamicRecordValidator.class), jdbc,
                mock(CurrentUser.class), mock(AuditService.class), mock(TemplateOperationLock.class));
        TemplateVersion version = new TemplateVersion();
        version.setId(UUID.randomUUID()); version.setPhysicalSchema("app_data"); version.setPhysicalTable("dyn_case_v1");
        when(versions.findFirstByTemplateCodeIgnoreCaseAndStatusOrderByVersionNoDesc("CASE", VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(fields.findByTemplateVersionIdOrderByDisplayOrderAsc(version.getId())).thenReturn(List.of(
                field("title", "title", FieldDataType.STRING, true),
                field("category", "category", FieldDataType.LIST, true),
                field("amount", "amount", FieldDataType.DECIMAL, true),
                field("notes", "notes", FieldDataType.TEXTAREA, false)));
        when(rules.findByFieldIdIn(anyCollection())).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());
    }

    @Test
    void combinesKeywordDateStatusAndDocumentFilters() {
        SearchRequest request = new SearchRequest(List.of(), List.of(), 0, 20, "50%_done",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "updatedAt", "completed", true);

        service.search("CASE", request);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), params.capture(), eq(Long.class));
        assertThat(sql.getValue())
                .contains("LOWER(CAST(title AS TEXT))", " OR LOWER(CAST(category AS TEXT))")
                .doesNotContain("CAST(amount AS TEXT)", "CAST(notes AS TEXT)")
                .contains("updated_at >= :fromDate", "updated_at < :toDateExclusive")
                .contains("record_status = :recordStatus", "source_document_id IS NOT NULL");

        MapSqlParameterSource values = (MapSqlParameterSource) params.getValue();
        assertThat(values.getValue("keyword")).isEqualTo("%50!%!_done%");
        assertThat(((Timestamp) values.getValue("fromDate")).toInstant()).isEqualTo(Instant.parse("2026-08-31T17:00:00Z"));
        assertThat(((Timestamp) values.getValue("toDateExclusive")).toInstant()).isEqualTo(Instant.parse("2026-09-02T17:00:00Z"));
        assertThat(values.getValue("recordStatus")).isEqualTo("COMPLETED");
    }

    @Test
    void rejectsInvalidRangeDateFieldAndStatus() {
        assertThatThrownBy(() -> service.search("CASE", new SearchRequest(List.of(), List.of(), 0, 20, null,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), "createdAt", null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Ngày bắt đầu không được sau ngày kết thúc");
        assertThatThrownBy(() -> service.search("CASE", new SearchRequest(List.of(), List.of(), 0, 20, null,
                LocalDate.of(2026, 9, 1), null, "businessDate", null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Loại ngày dùng để lọc không hợp lệ");
        assertThatThrownBy(() -> service.search("CASE", new SearchRequest(List.of(), List.of(), 0, 20, null,
                null, null, null, "DELETED", null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Trạng thái hồ sơ dùng để lọc không hợp lệ");
    }

    private FieldDefinition field(String code, String column, FieldDataType type, boolean searchable) {
        FieldDefinition field = new FieldDefinition(); field.setId(UUID.randomUUID()); field.setFieldCode(code);
        field.setColumnName(column); field.setDataType(type); field.setSearchable(searchable); field.setSortable(false);
        return field;
    }
}
