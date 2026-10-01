package com.example.dynamicform.security;

import com.example.dynamicform.security.SecurityDtos.AuthResponse;
import com.example.dynamicform.security.SecurityDtos.LoginRequest;
import com.example.dynamicform.security.SecurityDtos.ChangePasswordRequest;
import com.example.dynamicform.security.SecurityDtos.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_COOKIE = "refresh_token";
    private final SecurityService service;

    @Value("${app.security.refresh-token-days:7}")
    private long refreshTokenDays;

    @Value("${app.security.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                              HttpServletResponse response) {
        SecurityService.LoginResult result = service.login(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        setRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return service.currentUser(authentication.getName());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        SecurityService.LoginResult result = service.refresh(cookie(request, REFRESH_COOKIE), clientIp(request),
                request.getHeader("User-Agent"));
        setRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(cookie(request, REFRESH_COOKIE));
        clearRefreshCookie(response);
    }

    @PostMapping("/change-password")
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request,
                               HttpServletResponse response) {
        service.changePassword(authentication.getName(), request);
        clearRefreshCookie(response);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(refreshCookieSecure).sameSite("Strict").path("/api/v1/auth")
                .maxAge(Duration.ofDays(refreshTokenDays)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(refreshCookieSecure).sameSite("Strict").path("/api/v1/auth").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(item -> name.equals(item.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
