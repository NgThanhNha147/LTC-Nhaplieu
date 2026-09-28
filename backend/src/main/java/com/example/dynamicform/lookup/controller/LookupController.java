package com.example.dynamicform.lookup.controller;

import com.example.dynamicform.lookup.dto.LookupDtos.*;
import com.example.dynamicform.lookup.service.LookupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lookups")
@RequiredArgsConstructor
public class LookupController {
    private final LookupService service;
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public LookupResponse create(@Valid @RequestBody LookupRequest request) { return service.create(request); }
    @GetMapping public List<LookupResponse> list() { return service.list(); }
    @PutMapping("/{id}") public LookupResponse update(@PathVariable UUID id, @Valid @RequestBody LookupRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
    @GetMapping("/{code}/options") public OptionPage options(@PathVariable String code,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String parentValue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.options(code, query, parentValue, page, size); }
}
