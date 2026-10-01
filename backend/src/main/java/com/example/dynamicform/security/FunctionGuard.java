package com.example.dynamicform.security;

import com.example.dynamicform.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class FunctionGuard {
    private final boolean securityEnabled;

    public FunctionGuard(@Value("${app.security.enabled:true}") boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public void require(String functionCode) {
        if (!securityEnabled) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("FUNC_" + functionCode));
        if (!granted) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Bạn không có quyền thực hiện chức năng này");
        }
    }
}
