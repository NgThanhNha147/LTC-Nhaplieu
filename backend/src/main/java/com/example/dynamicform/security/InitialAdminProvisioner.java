package com.example.dynamicform.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InitialAdminProvisioner implements ApplicationRunner {
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final SecurityRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.enabled:true}") private boolean securityEnabled;
    @Value("${app.security.initial-admin.enabled:false}") private boolean enabled;
    @Value("${app.security.initial-admin.username:admin}") private String username;
    @Value("${app.security.initial-admin.password:ChangeMe123!}") private String password;
    @Value("${app.security.initial-admin.display-name:Quản trị hệ thống}") private String displayName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!securityEnabled || !enabled || repository.findUserByUsername(username.trim().toLowerCase()).isPresent()) return;
        if (password == null || password.length() < 12 || password.startsWith("replace_") || password.equals("ChangeMe123!")) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must be replaced with a unique password of at least 12 characters.");
        }
        UUID id = UUID.randomUUID();
        repository.createUser(id, username.trim().toLowerCase(), passwordEncoder.encode(password), displayName,
                null, true, "bootstrap");
        repository.assignUserRoles(id, Set.of(ADMIN_ROLE_ID), "bootstrap");
        log.warn("Initial administrator '{}' was created. Change its password before production use.", username);
    }
}
