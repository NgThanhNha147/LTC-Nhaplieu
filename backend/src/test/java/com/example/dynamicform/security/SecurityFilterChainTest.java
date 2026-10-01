package com.example.dynamicform.security;

import com.example.dynamicform.audit.AuditEventService;
import com.example.dynamicform.common.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityFilterChainTest.TestController.class, properties = {
        "app.security.enabled=true",
        "app.security.jwt-secret=test-secret-at-least-thirty-two-bytes-long"
})
@Import({SecurityConfig.class, SecurityAuditHandlers.class, SecurityFilterChainTest.TestController.class})
class SecurityFilterChainTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private DatabaseApiAuthorizationManager authorizationManager;
    @MockitoBean private AuditEventService auditEventService;

    @BeforeEach
    void denyByDefault() {
        when(authorizationManager.check(any(), any())).thenReturn(new AuthorizationDecision(false));
    }

    @Test
    void protectedApiReturns401WithoutBearerToken() throws Exception {
        mvc.perform(get("/api/v1/security-test")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiReturns403WhenPermissionIsDenied() throws Exception {
        mvc.perform(get("/api/v1/security-test").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void protectedApiAllowsAuthorizedToken() throws Exception {
        when(authorizationManager.check(any(), any())).thenReturn(new AuthorizationDecision(true));
        mvc.perform(get("/api/v1/security-test").with(jwt())).andExpect(status().isOk());
    }

    @RestController
    static class TestController {
        @GetMapping("/api/v1/security-test")
        String test() { return "ok"; }
    }
}
