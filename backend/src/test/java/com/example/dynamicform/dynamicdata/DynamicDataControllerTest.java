package com.example.dynamicform.dynamicdata;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.SearchPage;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.SearchRequest;
import com.example.dynamicform.security.FunctionGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicDataControllerTest {
    @Mock DynamicDataService service;
    @Mock FunctionGuard functions;
    @InjectMocks DynamicDataController controller;

    @Test
    void legacySearchRequiresGlobalRecordPermission() {
        SearchRequest request = new SearchRequest(List.of(), List.of(), 0, 20,
                null, null, null, null, null, null);
        when(service.search("CASE", request)).thenReturn(new SearchPage(List.of(), 0, 20, 0, 0));

        controller.search("CASE", request);

        verify(functions).require("RECORD_EXPORT");
    }

    @Test
    void legacyWriteEndpointIsDisabled() {
        assertThatThrownBy(() -> controller.create("CASE", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Việc của tôi");
    }
}

