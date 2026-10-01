package com.example.dynamicform.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SecurityDtos {
    private SecurityDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 200) String newPassword) {}
    public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {}
    public record MenuResponse(UUID id, String code, String label, String path, String icon,
                               int displayOrder, List<MenuResponse> children) {}
    public record UserResponse(UUID id, String username, String displayName, String email, String status,
                               boolean mustChangePassword, long rowVersion, Set<String> roles,
                               Set<String> functions, List<MenuResponse> menus) {}

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = 8, max = 200) String password,
            @NotBlank @Size(max = 255) String displayName,
            @Email @Size(max = 255) String email,
            Set<UUID> roleIds,
            boolean mustChangePassword) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 255) String displayName,
            @Email @Size(max = 255) String email,
            @NotBlank String status,
            @Size(min = 8, max = 200) String newPassword,
            boolean mustChangePassword,
            long rowVersion) {}

    public record AssignIdsRequest(Set<UUID> ids) {}

    public record RoleRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description,
            boolean active,
            long rowVersion) {}

    public record RoleResponse(UUID id, String code, String name, String description, boolean systemRole,
                               boolean active, long rowVersion, Set<String> functions) {}

    public record FunctionResponse(UUID id, String code, String name, String functionGroup, String description) {}
}
