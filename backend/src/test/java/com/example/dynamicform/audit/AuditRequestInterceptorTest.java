package com.example.dynamicform.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditRequestInterceptorTest {
    private final AuditEventService auditEvents = mock(AuditEventService.class);
    private final AuditRequestInterceptor interceptor = new AuditRequestInterceptor(auditEvents);

    @Test
    void auditsMutatingApiRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/templates");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditEvents).log(eq("BUSINESS_OPERATION"), eq("POST /api/v1/templates"),
                eq("API"), eq("/api/v1/templates"), eq("SUCCESS"), isNull(), anyMap());
    }

    @Test
    void skipsReadOnlyRecordSearch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/templates/FORM/records/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        verifyNoInteractions(auditEvents);
    }
}
