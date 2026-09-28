package com.example.dynamicform.template.controller;

import com.example.dynamicform.template.dto.TemplateDtos.*;
import com.example.dynamicform.template.service.MetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MetadataController {
    private final MetadataService metadata;

    @PostMapping("/template-versions/{versionId}/groups") @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@PathVariable UUID versionId, @Valid @RequestBody GroupRequest request) { return metadata.createGroup(versionId, request); }
    @GetMapping("/template-versions/{versionId}/groups") public List<GroupResponse> groups(@PathVariable UUID versionId) { return metadata.listGroups(versionId); }
    @PutMapping("/groups/{id}") public GroupResponse updateGroup(@PathVariable UUID id, @Valid @RequestBody GroupRequest request) { return metadata.updateGroup(id, request); }
    @DeleteMapping("/groups/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteGroup(@PathVariable UUID id) { metadata.deleteGroup(id); }

    @PostMapping("/template-versions/{versionId}/fields") @ResponseStatus(HttpStatus.CREATED)
    public FieldResponse createField(@PathVariable UUID versionId, @Valid @RequestBody FieldRequest request) { return metadata.createField(versionId, request); }
    @GetMapping("/template-versions/{versionId}/fields") public List<FieldResponse> fields(@PathVariable UUID versionId) { return metadata.listFields(versionId); }
    @PutMapping("/fields/{id}") public FieldResponse updateField(@PathVariable UUID id, @Valid @RequestBody FieldRequest request) { return metadata.updateField(id, request); }
    @DeleteMapping("/fields/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteField(@PathVariable UUID id) { metadata.deleteField(id); }

    @PostMapping("/template-versions/{versionId}/form-rules") @ResponseStatus(HttpStatus.CREATED)
    public FormRuleResponse createFormRule(@PathVariable UUID versionId, @Valid @RequestBody FormRuleRequest request) { return metadata.createFormRule(versionId, request); }
    @GetMapping("/template-versions/{versionId}/form-rules") public List<FormRuleResponse> formRules(@PathVariable UUID versionId) { return metadata.listFormRules(versionId); }
    @PutMapping("/form-rules/{id}") public FormRuleResponse updateFormRule(@PathVariable UUID id, @Valid @RequestBody FormRuleRequest request) { return metadata.updateFormRule(id, request); }
    @DeleteMapping("/form-rules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteFormRule(@PathVariable UUID id) { metadata.deleteFormRule(id); }
}
