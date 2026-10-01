package com.example.dynamicform.batch;

import com.example.dynamicform.batch.BatchDtos.*;
import com.example.dynamicform.security.FunctionGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-items")
@RequiredArgsConstructor
public class WorkItemController {
    private final BatchWorkflowService service;
    private final FunctionGuard functions;

    @GetMapping
    public WorkItemPage list(@RequestParam(required = false) UUID batchId,
                             @RequestParam(required = false) WorkflowStatus status,
                             @RequestParam(required = false) List<WorkflowStatus> statuses,
                             @RequestParam(required = false) UUID assignedUserId,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) LocalDate fromDate,
                             @RequestParam(required = false) LocalDate toDate,
                             @RequestParam(required = false) Boolean deleted,
                             @RequestParam(required = false) Boolean mine,
                             @RequestParam(defaultValue = "0") Integer page,
                             @RequestParam(defaultValue = "20") Integer size) {
        return service.listWorkItems(new WorkItemFilter(batchId, status, statuses, assignedUserId, keyword,
                fromDate, toDate, deleted, mine, page, size));
    }

    @GetMapping("/{id}")
    public WorkItemDetail get(@PathVariable UUID id) { return service.getWorkItem(id); }

    @GetMapping("/{id}/previous")
    public WorkItemSummary previous(@PathVariable UUID id) { return service.adjacent(id, false); }

    @GetMapping("/{id}/next")
    public WorkItemSummary next(@PathVariable UUID id) { return service.adjacent(id, true); }

    @PutMapping("/{id}/draft")
    public WorkItemDetail saveDraft(@PathVariable UUID id, @Valid @RequestBody SaveWorkItemRequest request) {
        return service.saveDraft(id, request);
    }

    @PostMapping("/{id}/submit")
    public WorkItemDetail submit(@PathVariable UUID id, @Valid @RequestBody SaveWorkItemRequest request) {
        functions.require("RECORD_SUBMIT");
        return service.submit(id, request);
    }

    @PostMapping("/{id}/approve")
    public WorkItemDetail approve(@PathVariable UUID id, @Valid @RequestBody WorkflowActionRequest request) {
        functions.require("RECORD_APPROVE");
        return service.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public WorkItemDetail reject(@PathVariable UUID id, @Valid @RequestBody RejectWorkItemRequest request) {
        functions.require("RECORD_REJECT");
        return service.reject(id, request);
    }

    @PostMapping("/{id}/reopen")
    public WorkItemDetail reopen(@PathVariable UUID id, @Valid @RequestBody WorkflowActionRequest request) {
        functions.require("RECORD_REOPEN");
        return service.reopen(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam long rowVersion) { service.delete(id, rowVersion); }

    @PostMapping("/{id}/restore")
    public WorkItemDetail restore(@PathVariable UUID id, @Valid @RequestBody WorkflowActionRequest request) {
        functions.require("RECORD_RESTORE");
        return service.restore(id, request);
    }
}
