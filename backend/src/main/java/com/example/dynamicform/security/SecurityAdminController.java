package com.example.dynamicform.security;

import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.security.SecurityDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class SecurityAdminController {
    private final SecurityService service;
    private final CurrentUser currentUser;

    @GetMapping("/users")
    public List<UserResponse> users() { return service.users(); }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return service.createUser(request, currentUser.username());
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.updateUser(id, request, currentUser.username());
    }

    @PutMapping("/users/{id}/roles")
    public UserResponse assignRoles(@PathVariable UUID id, @RequestBody AssignIdsRequest request) {
        return service.assignRoles(id, request, currentUser.username());
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() { return service.roles(); }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@Valid @RequestBody RoleRequest request) {
        return service.createRole(request, currentUser.username());
    }

    @PutMapping("/roles/{id}")
    public RoleResponse updateRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return service.updateRole(id, request, currentUser.username());
    }

    @PutMapping("/roles/{id}/functions")
    public RoleResponse assignFunctions(@PathVariable UUID id, @RequestBody AssignIdsRequest request) {
        return service.assignFunctions(id, request, currentUser.username());
    }

    @GetMapping("/functions")
    public List<FunctionResponse> functions() { return service.functions(); }
}
