package com.example.dynamicform.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SecurityRepository {
    private final JdbcTemplate jdbc;

    public record UserRow(UUID id, String username, String passwordHash, String displayName, String email,
                          String status, int failedAttempts, Instant lockedUntil, boolean mustChangePassword,
                          long rowVersion) {}
    public record RoleRow(UUID id, String code, String name, String description, boolean systemRole,
                          boolean active, long rowVersion) {}
    public record MenuRow(UUID id, UUID parentId, String code, String label, String path, String icon,
                          int displayOrder) {}
    public record ApiResource(String method, String pathPattern, String functionCode) {}
    public record RefreshTokenRow(UUID id, UUID userId, String username, Instant expiresAt) {}

    public Optional<UserRow> findUserByUsername(String username) {
        return jdbc.query("""
                SELECT id, username, password_hash, display_name, email, status, failed_login_attempts,
                       locked_until, must_change_password, row_version
                FROM app_meta.sec_user WHERE username = ?
                """, this::mapUser, username).stream().findFirst();
    }

    public Optional<UserRow> findUserById(UUID id) {
        return jdbc.query("""
                SELECT id, username, password_hash, display_name, email, status, failed_login_attempts,
                       locked_until, must_change_password, row_version
                FROM app_meta.sec_user WHERE id = ?
                """, this::mapUser, id).stream().findFirst();
    }

    public List<UserRow> listUsers() {
        return jdbc.query("""
                SELECT id, username, password_hash, display_name, email, status, failed_login_attempts,
                       locked_until, must_change_password, row_version
                FROM app_meta.sec_user ORDER BY username
                """, this::mapUser);
    }

    public void createUser(UUID id, String username, String passwordHash, String displayName, String email,
                           boolean mustChangePassword, String actor) {
        jdbc.update("""
                INSERT INTO app_meta.sec_user
                    (id, username, password_hash, display_name, email, must_change_password, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, username, passwordHash, displayName, email, mustChangePassword, actor, actor);
    }

    public int updateUser(UUID id, String displayName, String email, String status, String passwordHash,
                          boolean mustChangePassword, long rowVersion, String actor) {
        if (passwordHash == null) {
            return jdbc.update("""
                    UPDATE app_meta.sec_user
                    SET display_name = ?, email = ?, status = ?, must_change_password = ?,
                        updated_at = CURRENT_TIMESTAMP, updated_by = ?, row_version = row_version + 1
                    WHERE id = ? AND row_version = ?
                    """, displayName, email, status, mustChangePassword, actor, id, rowVersion);
        }
        return jdbc.update("""
                UPDATE app_meta.sec_user
                SET display_name = ?, email = ?, status = ?, password_hash = ?, password_changed_at = CURRENT_TIMESTAMP,
                    must_change_password = ?, failed_login_attempts = 0, locked_until = NULL,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?, row_version = row_version + 1
                WHERE id = ? AND row_version = ?
                """, displayName, email, status, passwordHash, mustChangePassword, actor, id, rowVersion);
    }

    public void changePassword(UUID userId, String passwordHash, String actor) {
        jdbc.update("""
                UPDATE app_meta.sec_user
                SET password_hash = ?, password_changed_at = CURRENT_TIMESTAMP, must_change_password = FALSE,
                    failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?, row_version = row_version + 1
                WHERE id = ?
                """, passwordHash, actor, userId);
    }

    public void loginSucceeded(UUID userId) {
        jdbc.update("UPDATE app_meta.sec_user SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?", userId);
    }

    public void loginFailed(UUID userId, int maxAttempts, Instant lockedUntil) {
        jdbc.update("""
                UPDATE app_meta.sec_user
                SET failed_login_attempts = failed_login_attempts + 1,
                    locked_until = CASE WHEN failed_login_attempts + 1 >= ? THEN ? ELSE locked_until END
                WHERE id = ?
                """, maxAttempts, Timestamp.from(lockedUntil), userId);
    }

    public Set<String> roles(UUID userId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT r.code FROM app_meta.sec_role r
                JOIN app_meta.sec_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.active = TRUE ORDER BY r.code
                """, String.class, userId));
    }

    public Set<String> functions(UUID userId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT f.code FROM app_meta.sec_function f
                JOIN app_meta.sec_role_function rf ON rf.function_id = f.id
                JOIN app_meta.sec_role r ON r.id = rf.role_id AND r.active = TRUE
                JOIN app_meta.sec_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND f.active = TRUE ORDER BY f.code
                """, String.class, userId));
    }

    public List<MenuRow> menus(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT m.id, m.parent_id, m.code, m.label, m.path, m.icon, m.display_order
                FROM app_meta.sec_menu m
                JOIN app_meta.sec_menu_function mf ON mf.menu_id = m.id
                JOIN app_meta.sec_role_function rf ON rf.function_id = mf.function_id
                JOIN app_meta.sec_user_role ur ON ur.role_id = rf.role_id
                JOIN app_meta.sec_role r ON r.id = ur.role_id AND r.active = TRUE
                JOIN app_meta.sec_function f ON f.id = rf.function_id AND f.active = TRUE
                WHERE ur.user_id = ? AND m.active = TRUE
                ORDER BY m.display_order, m.label
                """, (rs, rowNum) -> new MenuRow(rs.getObject("id", UUID.class),
                rs.getObject("parent_id", UUID.class), rs.getString("code"), rs.getString("label"),
                rs.getString("path"), rs.getString("icon"), rs.getInt("display_order")), userId);
    }

    public boolean hasFunction(String username, String functionCode) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_meta.sec_user u
                JOIN app_meta.sec_user_role ur ON ur.user_id = u.id
                JOIN app_meta.sec_role r ON r.id = ur.role_id AND r.active = TRUE
                JOIN app_meta.sec_role_function rf ON rf.role_id = r.id
                JOIN app_meta.sec_function f ON f.id = rf.function_id AND f.active = TRUE
                WHERE u.username = ? AND u.status = 'ACTIVE' AND u.must_change_password = FALSE AND f.code = ?
                """, Integer.class, username, functionCode);
        return count != null && count > 0;
    }

    public List<ApiResource> apiResources() {
        return jdbc.query("""
                SELECT http_method, path_pattern, function_code
                FROM app_meta.sec_api_resource WHERE active = TRUE ORDER BY priority, path_pattern
                """, (rs, rowNum) -> new ApiResource(rs.getString(1), rs.getString(2), rs.getString(3)));
    }

    public List<RoleRow> listRoles() {
        return jdbc.query("""
                SELECT id, code, name, description, system_role, active, row_version
                FROM app_meta.sec_role ORDER BY code
                """, this::mapRole);
    }

    public Optional<RoleRow> findRole(UUID id) {
        return jdbc.query("""
                SELECT id, code, name, description, system_role, active, row_version
                FROM app_meta.sec_role WHERE id = ?
                """, this::mapRole, id).stream().findFirst();
    }

    public Set<String> roleFunctions(UUID roleId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT f.code FROM app_meta.sec_function f
                JOIN app_meta.sec_role_function rf ON rf.function_id = f.id
                WHERE rf.role_id = ? ORDER BY f.code
                """, String.class, roleId));
    }

    public UUID createRole(String code, String name, String description, boolean active, String actor) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_meta.sec_role
                    (id, code, name, description, active, system_role, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?)
                """, id, code, name, description, active, actor, actor);
        return id;
    }

    public int updateRole(UUID id, String code, String name, String description, boolean active,
                          long rowVersion, String actor) {
        return jdbc.update("""
                UPDATE app_meta.sec_role
                SET code = ?, name = ?, description = ?, active = ?, updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?, row_version = row_version + 1
                WHERE id = ? AND row_version = ?
                """, code, name, description, active, actor, id, rowVersion);
    }

    public void assignUserRoles(UUID userId, Set<UUID> roleIds, String actor) {
        jdbc.update("DELETE FROM app_meta.sec_user_role WHERE user_id = ?", userId);
        roleIds.forEach(roleId -> jdbc.update("""
                INSERT INTO app_meta.sec_user_role(user_id, role_id, assigned_by) VALUES (?, ?, ?)
                """, userId, roleId, actor));
    }

    public void assignRoleFunctions(UUID roleId, Set<UUID> functionIds, String actor) {
        jdbc.update("DELETE FROM app_meta.sec_role_function WHERE role_id = ?", roleId);
        functionIds.forEach(functionId -> jdbc.update("""
                INSERT INTO app_meta.sec_role_function(role_id, function_id, assigned_by) VALUES (?, ?, ?)
                """, roleId, functionId, actor));
    }

    public List<SecurityDtos.FunctionResponse> listFunctions() {
        return jdbc.query("""
                SELECT id, code, name, function_group, description
                FROM app_meta.sec_function WHERE active = TRUE ORDER BY function_group, code
                """, (rs, rowNum) -> new SecurityDtos.FunctionResponse(rs.getObject("id", UUID.class),
                rs.getString("code"), rs.getString("name"), rs.getString("function_group"),
                rs.getString("description")));
    }

    public void storeRefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt,
                                  String ip, String userAgent) {
        jdbc.update("""
                INSERT INTO app_meta.sec_refresh_token
                    (id, user_id, token_hash, expires_at, created_ip, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, userId, tokenHash, Timestamp.from(expiresAt), ip, userAgent);
    }

    public Optional<RefreshTokenRow> findActiveRefreshToken(String tokenHash) {
        return jdbc.query("""
                SELECT t.id, t.user_id, u.username, t.expires_at
                FROM app_meta.sec_refresh_token t
                JOIN app_meta.sec_user u ON u.id = t.user_id
                WHERE t.token_hash = ? AND t.revoked_at IS NULL AND t.expires_at > CURRENT_TIMESTAMP
                  AND u.status = 'ACTIVE'
                """, (rs, rowNum) -> new RefreshTokenRow(rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("username"),
                rs.getTimestamp("expires_at").toInstant()), tokenHash).stream().findFirst();
    }

    public int revokeRefreshToken(UUID id, UUID replacementId) {
        return jdbc.update("""
                UPDATE app_meta.sec_refresh_token SET revoked_at = CURRENT_TIMESTAMP, replaced_by = ?
                WHERE id = ? AND revoked_at IS NULL
                """, replacementId, id);
    }

    public void revokeRefreshToken(String tokenHash) {
        jdbc.update("""
                UPDATE app_meta.sec_refresh_token SET revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = ? AND revoked_at IS NULL
                """, tokenHash);
    }

    public void revokeAllRefreshTokens(UUID userId) {
        jdbc.update("""
                UPDATE app_meta.sec_refresh_token SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND revoked_at IS NULL
                """, userId);
    }

    public void recordLogin(UUID userId, String username, boolean successful, String reason,
                            String ip, String userAgent) {
        jdbc.update("""
                INSERT INTO app_meta.sec_login_history
                    (id, user_id, username, successful, failure_reason, ip_address, user_agent)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, username, successful, reason, ip, userAgent);
    }

    private UserRow mapUser(ResultSet rs, int rowNum) throws SQLException {
        Timestamp locked = rs.getTimestamp("locked_until");
        return new UserRow(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("password_hash"), rs.getString("display_name"), rs.getString("email"),
                rs.getString("status"), rs.getInt("failed_login_attempts"),
                locked == null ? null : locked.toInstant(), rs.getBoolean("must_change_password"),
                rs.getLong("row_version"));
    }

    private RoleRow mapRole(ResultSet rs, int rowNum) throws SQLException {
        return new RoleRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getBoolean("system_role"), rs.getBoolean("active"),
                rs.getLong("row_version"));
    }
}
