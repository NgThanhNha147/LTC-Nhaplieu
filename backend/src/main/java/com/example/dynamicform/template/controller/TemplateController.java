package com.example.dynamicform.template.controller;

import com.example.dynamicform.template.dto.TemplateDtos.*;
import com.example.dynamicform.template.service.*;
import com.example.dynamicform.schema.SchemaGeneratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templates;
    private final MetadataService metadata;
    private final SchemaGeneratorService schema;

    @PostMapping("/templates") @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody TemplateRequest request) { return templates.create(request); }
    @GetMapping("/templates") public List<TemplateResponse> list() { return templates.list(); }
    @GetMapping("/templates/{id}") public TemplateResponse get(@PathVariable UUID id) { return templates.get(id); }
    @PutMapping("/templates/{id}") public TemplateResponse update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) { return templates.update(id, request); }
    @DeleteMapping("/templates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { templates.delete(id); }
    @PostMapping("/templates/{id}/versions") @ResponseStatus(HttpStatus.CREATED) public VersionResponse version(@PathVariable UUID id) { return templates.createVersion(id); }
    @GetMapping("/templates/{id}/versions") public List<VersionResponse> versions(@PathVariable UUID id) { return templates.versions(id); }
    @GetMapping("/templates/{id}/versions/{versionNo}") public VersionResponse version(@PathVariable UUID id, @PathVariable Integer versionNo) { return templates.getVersion(id, versionNo); }

    @PostMapping("/template-versions/{id}/validate") public ValidationResult validate(@PathVariable UUID id) { return schema.validate(id); }
    @GetMapping("/template-versions/{id}/migration-plan") public MigrationPlan migrationPlan(@PathVariable UUID id) { return schema.migrationPlan(id); }
    @GetMapping("/template-versions/{id}/ddl-preview") public DdlPreview preview(@PathVariable UUID id) { return schema.preview(id); }
    @PostMapping("/template-versions/{id}/generate") public VersionResponse generate(@PathVariable UUID id) { return templates.map(schema.generate(id)); }
    @PostMapping("/template-versions/{id}/publish") public VersionResponse publish(@PathVariable UUID id) { return templates.map(schema.publish(id)); }
    @GetMapping("/template-versions/{id}/storage-check") public StorageCheck storageCheck(@PathVariable UUID id) { return schema.storageCheck(id); }
    @PostMapping("/template-versions/{id}/cancel-generated") public VersionResponse cancelGenerated(@PathVariable UUID id) { return templates.map(schema.cancelGenerated(id)); }
    @DeleteMapping("/template-versions/{id}/storage") public VersionResponse purgeArchived(@PathVariable UUID id, @RequestParam(required = false) String reason) { return templates.map(schema.purgeArchived(id, reason)); }
}
