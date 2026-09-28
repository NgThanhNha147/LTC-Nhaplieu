package com.example.dynamicform.export;

import com.example.dynamicform.dynamicdata.DynamicDataService;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.*;
import com.example.dynamicform.template.domain.TemplateVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportServiceTest {
    @Test
    void forwardsAllSearchCriteriaWhilePaginating() {
        DynamicDataService dynamicData = mock(DynamicDataService.class);
        TemplateVersion version = new TemplateVersion();
        when(dynamicData.context("CASE")).thenReturn(new DynamicDataService.Context(version, List.of(), List.of()));
        when(dynamicData.search(eq("CASE"), any(SearchRequest.class))).thenReturn(new SearchPage(List.of(), 0, 200, 0, 0));
        ExportService service = new ExportService(dynamicData);
        ReflectionTestUtils.setField(service, "maxRows", 1000);
        SearchRequest request = new SearchRequest(List.of(), List.of(), 7, 35, "needle",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "updatedAt", "DRAFT", false);

        service.export("CASE", request, "csv");

        ArgumentCaptor<SearchRequest> forwarded = ArgumentCaptor.forClass(SearchRequest.class);
        verify(dynamicData).search(eq("CASE"), forwarded.capture());
        assertThat(forwarded.getValue().keyword()).isEqualTo("needle");
        assertThat(forwarded.getValue().fromDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(forwarded.getValue().toDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(forwarded.getValue().dateField()).isEqualTo("updatedAt");
        assertThat(forwarded.getValue().status()).isEqualTo("DRAFT");
        assertThat(forwarded.getValue().hasDocument()).isFalse();
        assertThat(forwarded.getValue().page()).isZero();
        assertThat(forwarded.getValue().size()).isEqualTo(200);
    }
}
