package com.example.dynamicform.security;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.security.SecurityDtos.*;
import com.example.dynamicform.security.SecurityRepository.MenuRow;
import com.example.dynamicform.security.SecurityRepository.RoleRow;
import com.example.dynamicform.security.SecurityRepository.UserRow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final SecurityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public LoginResult login(LoginRequest request, String ip, String userAgent) {
        String username = normalizeUsername(request.username());
        UserRow user = repository.findUserByUsername(username).orElse(null);
        if (user == null) {
            repository.recordLogin(null, username, false, "INVALID_CREDENTIALS", ip, userAgent);
            throw unauthorized();
        }
        if (!"ACTIVE".equals(user.status())) {
            repository.recordLogin(user.id(), username, false, "ACCOUNT_" + user.status(), ip, userAgent);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản không được phép đăng nhập");
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now())) {
            repository.recordLogin(user.id(), username, false, "ACCOUNT_TEMPORARILY_LOCKED", ip, userAgent);
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Tài khoản tạm khóa do đăng nhập sai nhiều lần");
        }
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            repository.loginFailed(user.id(), MAX_FAILED_ATTEMPTS, Instant.now().plus(LOCK_DURATION));
            repository.recordLogin(user.id(), username, false, "INVALID_CREDENTIALS", ip, userAgent);
            throw unauthorized();
        }

        repository.loginSucceeded(user.id());
        repository.recordLogin(user.id(), username, true, null, ip, userAgent);
        UserResponse principal = toUser(user);
        TokenService.AccessToken accessToken = tokenService.issueAccessToken(user, principal.roles(), principal.functions());
        String refreshToken = issueRefreshToken(user.id(), ip, userAgent);
        return new LoginResult(new AuthResponse(accessToken.value(), accessToken.expiresIn(), principal), refreshToken);
    }

    @Transactional
    public LoginResult refresh(String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) throw unauthorized();
        String hash = tokenService.hashRefreshToken(rawToken);
        var current = repository.findActiveRefreshToken(hash).orElseThrow(this::unauthorized);
        UserRow user = repository.findUserById(current.userId()).orElseThrow(this::unauthorized);
        if (!"ACTIVE".equals(user.status())
                || (user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now()))) {
            repository.revokeRefreshToken(current.id(), null);
            throw unauthorized();
        }

        String replacementToken = tokenService.newRefreshToken();
        UUID replacementId = UUID.randomUUID();
        repository.storeRefreshToken(replacementId, user.id(), tokenService.hashRefreshToken(replacementToken),
                tokenService.refreshTokenExpiry(), ip, userAgent);
        if (repository.revokeRefreshToken(current.id(), replacementId) == 0) {
            throw unauthorized();
        }

        UserResponse principal = toUser(user);
        TokenService.AccessToken accessToken = tokenService.issueAccessToken(user, principal.roles(), principal.functions());
        return new LoginResult(new AuthResponse(accessToken.value(), accessToken.expiresIn(), principal), replacementToken);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            repository.revokeRefreshToken(tokenService.hashRefreshToken(rawToken));
        }
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        UserRow user = repository.findUserByUsername(normalizeUsername(username)).orElseThrow(this::unauthorized);
        if (!"ACTIVE".equals(user.status())) throw unauthorized();
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw ApiException.badRequest("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        repository.changePassword(user.id(), passwordEncoder.encode(request.newPassword()), user.username());
        repository.revokeAllRefreshTokens(user.id());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String username) {
        UserRow user = repository.findUserByUsername(normalizeUsername(username)).orElseThrow(this::unauthorized);
        if (!"ACTIVE".equals(user.status())) throw unauthorized();
        return toUser(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> users() {
        return repository.listUsers().stream().map(this::toUser).toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, String actor) {
        String username = normalizeUsername(request.username());
        if (repository.findUserByUsername(username).isPresent()) {
            throw ApiException.conflict("Tên đăng nhập đã tồn tại");
        }
        UUID id = UUID.randomUUID();
        repository.createUser(id, username, passwordEncoder.encode(request.password()), request.displayName().trim(),
                emptyToNull(request.email()), request.mustChangePassword(), actor);
        repository.assignUserRoles(id, safeIds(request.roleIds()), actor);
        return toUser(repository.findUserById(id).orElseThrow());
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, String actor) {
        UserRow existing = repository.findUserById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE", "LOCKED").contains(status)) {
            throw ApiException.badRequest("Trạng thái người dùng không hợp lệ");
        }
        if (existing.username().equalsIgnoreCase(actor) && !"ACTIVE".equals(status)) {
            throw ApiException.badRequest("Không thể tự khóa hoặc vô hiệu hóa tài khoản đang đăng nhập");
        }
        String passwordHash = request.newPassword() == null || request.newPassword().isBlank()
                ? null : passwordEncoder.encode(request.newPassword());
        int changed = repository.updateUser(id, request.displayName().trim(), emptyToNull(request.email()), status,
                passwordHash, request.mustChangePassword(), request.rowVersion(), actor);
        if (passwordHash != null || !"ACTIVE".equals(status)) repository.revokeAllRefreshTokens(id);
        if (changed == 0) throw ApiException.conflict("Người dùng đã được cập nhật bởi thao tác khác");
        return toUser(repository.findUserById(id).orElseThrow());
    }

    @Transactional
    public UserResponse assignRoles(UUID id, AssignIdsRequest request, String actor) {
        UserRow user = repository.findUserById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        if (user.username().equalsIgnoreCase(actor)) {
            throw ApiException.badRequest("Không thể tự thay đổi vai trò của tài khoản đang đăng nhập");
        }
        repository.assignUserRoles(id, safeIds(request.ids()), actor);
        repository.revokeAllRefreshTokens(id);
        return toUser(repository.findUserById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return repository.listRoles().stream().map(this::toRole).toList();
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request, String actor) {
        String code = normalizeCode(request.code());
        UUID id = repository.createRole(code, request.name().trim(), emptyToNull(request.description()),
                request.active(), actor);
        return toRole(repository.findRole(id).orElseThrow());
    }

    @Transactional
    public RoleResponse updateRole(UUID id, RoleRequest request, String actor) {
        RoleRow existing = repository.findRole(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy vai trò"));
        if (existing.systemRole() && !existing.code().equals(normalizeCode(request.code()))) {
            throw ApiException.badRequest("Không thể đổi mã vai trò hệ thống");
        }
        if (existing.systemRole() && !request.active()) {
            throw ApiException.badRequest("Không thể vô hiệu hóa vai trò hệ thống");
        }
        int changed = repository.updateRole(id, normalizeCode(request.code()), request.name().trim(),
                emptyToNull(request.description()), request.active(), request.rowVersion(), actor);
        if (changed == 0) throw ApiException.conflict("Vai trò đã được cập nhật bởi thao tác khác");
        return toRole(repository.findRole(id).orElseThrow());
    }

    @Transactional
    public RoleResponse assignFunctions(UUID id, AssignIdsRequest request, String actor) {
        RoleRow role = repository.findRole(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy vai trò"));
        if (role.systemRole()) {
            throw ApiException.badRequest("Vai trò hệ thống có bộ quyền cố định; hãy tạo vai trò mới để tùy chỉnh");
        }
        repository.assignRoleFunctions(id, safeIds(request.ids()), actor);
        return toRole(repository.findRole(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<FunctionResponse> functions() {
        return repository.listFunctions();
    }

    private String issueRefreshToken(UUID userId, String ip, String userAgent) {
        String token = tokenService.newRefreshToken();
        repository.storeRefreshToken(UUID.randomUUID(), userId, tokenService.hashRefreshToken(token),
                tokenService.refreshTokenExpiry(), ip, userAgent);
        return token;
    }

    private UserResponse toUser(UserRow user) {
        return new UserResponse(user.id(), user.username(), user.displayName(), user.email(), user.status(),
                user.mustChangePassword(), user.rowVersion(), repository.roles(user.id()),
                repository.functions(user.id()), buildMenus(repository.menus(user.id())));
    }

    private RoleResponse toRole(RoleRow role) {
        return new RoleResponse(role.id(), role.code(), role.name(), role.description(), role.systemRole(),
                role.active(), role.rowVersion(), repository.roleFunctions(role.id()));
    }

    private List<MenuResponse> buildMenus(List<MenuRow> rows) {
        Map<UUID, List<MenuRow>> children = new LinkedHashMap<>();
        rows.forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return children.getOrDefault(null, List.of()).stream().map(row -> toMenu(row, children)).toList();
    }

    private MenuResponse toMenu(MenuRow row, Map<UUID, List<MenuRow>> children) {
        List<MenuResponse> childMenus = children.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toMenu(child, children)).toList();
        return new MenuResponse(row.id(), row.code(), row.label(), row.path(), row.icon(), row.displayOrder(), childMenus);
    }

    private Set<UUID> safeIds(Set<UUID> ids) {
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng");
    }

    public record LoginResult(AuthResponse response, String refreshToken) {}
}
