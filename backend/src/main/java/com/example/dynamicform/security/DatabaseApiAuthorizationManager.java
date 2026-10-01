package com.example.dynamicform.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DatabaseApiAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final SecurityRepository repository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        Authentication principal = authentication.get();
        if (principal == null || !principal.isAuthenticated()) return new AuthorizationDecision(false);
        if (principal instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("mustChangePassword"))) {
            return new AuthorizationDecision(false);
        }

        HttpServletRequest request = context.getRequest();
        List<SecurityRepository.ApiResource> resources = repository.apiResources();
        for (SecurityRepository.ApiResource resource : resources) {
            if (resource.method().equalsIgnoreCase(request.getMethod())
                    && pathMatcher.match(resource.pathPattern(), request.getRequestURI())) {
                // Read current permissions from the database so role/account changes apply immediately.
                boolean granted = repository.hasFunction(principal.getName(), resource.functionCode());
                return new AuthorizationDecision(granted);
            }
        }
        // Auth and explicitly mapped admin/business resources are allowed elsewhere; unknown API endpoints fail closed.
        return new AuthorizationDecision(false);
    }
}
