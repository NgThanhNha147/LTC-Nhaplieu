package com.example.dynamicform.batch;

import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.DynamicRecord;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BatchDtos {
    private BatchDtos() {}

    public record CreateBatchRequest(
            @NotBlank @Size(max = 255) String batchName,
            @NotNull UUID templateVersionId,
            UUID assignedUserId,
            @Size(max = 5000) String description) {}

    public record AssignBatchRequest(
            @NotNull UUID assignedUserId,
            @NotNull @Min(0) Long rowVersion,
            @NotBlank @Size(max = 2000) String reason) {}

    public record BatchStatistics(long totalFiles, long unprocessed, long draft, long pendingApproval,
                                  long approved, long rejected, long deleted) {
        public long completedFiles() { return approved; }
    }

    public record BatchResponse(
            UUID id, String batchCode, String batchName,
            UUID templateId, String templateCode, String templateName,
            UUID templateVersionId, Integer templateVersionNo,
            BatchSourceType sourceType, String originalName,
            UUID assignedUserId, String assignedUserName, String description,
            String status, BatchStatistics statistics, Long rowVersion,
            Instant archivedAt, Instant createdAt, String createdBy, Instant updatedAt) {}

    public record BatchPage(List<BatchResponse> items, int page, int size, long totalElements, int totalPages) {}

    public record UploadError(String fileName, String code, String message) {}

    public record BatchUploadResponse(UUID batchId, int accepted, int duplicates, int rejected,
                                      List<WorkItemSummary> documents, List<UploadError> errors,
                                      BatchStatistics statistics) {}

    public record WorkItemSummary(
            UUID id, UUID batchId, String batchCode, String batchName,
            UUID templateVersionId, String templateCode, Integer templateVersionNo,
            Integer sequenceNo, UUID documentFileId, String documentUrl,
            String relativePath, String displayName, WorkflowStatus status,
            UUID assignedUserId, String assignedUserName, UUID dynamicRecordId,
            String rejectionReason, boolean deleted, Long rowVersion,
            Instant startedAt, Instant submittedAt, String submittedBy,
            Instant approvedAt, String approvedBy, Instant updatedAt) {}

    public record WorkItemDetail(WorkItemSummary item, DynamicRecord dynamicRecord,
                                 List<WorkflowHistoryResponse> history) {}

    public record WorkItemPage(List<WorkItemSummary> items, int page, int size,
                               long totalElements, int totalPages) {}

    public record SaveWorkItemRequest(
            @NotNull @Min(0) Long rowVersion,
            @Min(0) Long dynamicRowVersion,
            @NotNull Map<String, Object> data) {}

    public record WorkflowActionRequest(
            @NotNull @Min(0) Long rowVersion,
            @Size(max = 2000) String reason) {}

    public record RejectWorkItemRequest(
            @NotNull @Min(0) Long rowVersion,
            @NotBlank @Size(max = 2000) String rejectionReason) {}

    public record WorkflowHistoryResponse(UUID id, WorkflowStatus fromStatus, WorkflowStatus toStatus,
                                          String action, String comment, String performedBy,
                                          Instant performedAt) {}

    public record WorkItemFilter(UUID batchId, WorkflowStatus status, List<WorkflowStatus> statuses,
                                 UUID assignedUserId, String keyword, LocalDate fromDate, LocalDate toDate,
                                 Boolean deleted, Boolean mine, Integer page, Integer size) {}
}
