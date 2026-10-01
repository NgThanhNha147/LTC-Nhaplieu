package com.example.dynamicform.batch;

import com.example.dynamicform.batch.BatchDtos.*;
import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.document.DocumentRepository;
import com.example.dynamicform.document.DocumentService;
import com.example.dynamicform.dynamicdata.DynamicDataService;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.CreateRecordRequest;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.DynamicRecord;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.UpdateRecordRequest;
import com.example.dynamicform.security.SecurityRepository;
import com.example.dynamicform.template.domain.TemplateVersion;
import com.example.dynamicform.template.domain.VersionStatus;
import com.example.dynamicform.template.repository.TemplateVersionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class BatchWorkflowService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final IngestionBatchRepository batches;
    private final BatchDocumentRepository documents;
    private final RecordWorkflowHistoryRepository histories;
    private final TemplateVersionRepository versions;
    private final DocumentRepository documentFiles;
    private final DocumentService documentService;
    private final DynamicDataService dynamicData;
    private final SecurityRepository security;
    private final CurrentUser currentUser;

    @Value("${app.security.enabled:true}") private boolean securityEnabled;
    @Value("${app.batch.max-files:1000}") private int maxFiles;
    @Value("${app.batch.max-file-bytes:52428800}") private long maxFileBytes;
    @Value("${app.batch.max-total-bytes:1073741824}") private long maxTotalBytes;

    @Transactional
    public BatchResponse create(CreateBatchRequest request) {
        TemplateVersion version = versions.findById(request.templateVersionId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        if (version.getStatus() != VersionStatus.PUBLISHED || version.getPhysicalTable() == null
                || version.getPhysicalSchema() == null || "PURGED".equalsIgnoreCase(version.getStorageStatus())) {
            throw ApiException.conflict("Chỉ có thể tạo đợt hồ sơ từ phiên bản biểu mẫu đang được sử dụng và đã có bảng dữ liệu.");
        }
        UUID assignedUserId = resolveAssigneeForNewBatch(request.assignedUserId());
        Instant now = Instant.now();
        IngestionBatch batch = new IngestionBatch();
        batch.setId(UUID.randomUUID());
        batch.setBatchCode(nextBatchCode());
        batch.setBatchName(request.batchName().trim());
        batch.setTemplateVersion(version);
        batch.setAssignedUserId(assignedUserId);
        batch.setDescription(blankToNull(request.description()));
        batch.setCreatedAt(now); batch.setUpdatedAt(now);
        batch.setCreatedBy(currentUser.username()); batch.setUpdatedBy(currentUser.username());
        return mapBatch(batches.save(batch));
    }

    @Transactional(readOnly = true)
    public BatchPage list(String keyword, UUID templateId, UUID templateVersionId, UUID assignedUserId, Boolean archived,
                          int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        UUID visibleAssignee = canViewAll() ? assignedUserId : currentUserId();
        Specification<IngestionBatch> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("batchName")), value),
                        cb.like(cb.lower(root.get("batchCode")), value)));
            }
            if (templateId != null) predicates.add(cb.equal(root.get("templateVersion").get("template").get("id"), templateId));
            if (templateVersionId != null) predicates.add(cb.equal(root.get("templateVersion").get("id"), templateVersionId));
            if (visibleAssignee != null) predicates.add(cb.equal(root.get("assignedUserId"), visibleAssignee));
            if (archived != null) predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<IngestionBatch> result = batches.findAll(spec, PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return new BatchPage(result.getContent().stream().map(this::mapBatch).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(UUID id) { return mapBatch(requireVisibleBatch(id)); }

    @Transactional(readOnly = true)
    public BatchStatistics statistics(UUID id) { requireVisibleBatch(id); return statisticsFor(id); }

    @Transactional
    public BatchResponse assign(UUID id, AssignBatchRequest request) {
        validateAssignee(request.assignedUserId());
        IngestionBatch batch = requireBatch(id);
        checkVersion(batch.getRowVersion(), request.rowVersion());
        if (batch.getArchivedAt() != null) throw ApiException.conflict("Đợt hồ sơ đã được lưu trữ nên không thể chuyển giao.");
        if (Objects.equals(batch.getAssignedUserId(), request.assignedUserId())) {
            throw ApiException.badRequest("Người phụ trách mới đang là người được giao đợt hồ sơ này.");
        }
        BatchStatistics stats = statisticsFor(id);
        if (stats.pendingApproval() > 0 || stats.approved() > 0) {
            throw ApiException.conflict("Không thể chuyển giao khi đợt còn hồ sơ đang chờ duyệt hoặc đã duyệt. Hãy xử lý trạng thái các hồ sơ trước.");
        }
        UUID previousAssigneeId = batch.getAssignedUserId();
        String previousAssignee = userName(previousAssigneeId);
        String nextAssignee = userName(request.assignedUserId());
        batch.setAssignedUserId(request.assignedUserId());
        batch.setUpdatedAt(Instant.now()); batch.setUpdatedBy(currentUser.username());
        documents.findAll((root, query, cb) -> cb.equal(root.get("batch").get("id"), id))
                .forEach(item -> {
                    item.setAssignedUserId(request.assignedUserId());
                    item.setUpdatedAt(Instant.now());
                    addHistory(item, item.getWorkflowStatus(), item.getWorkflowStatus(), "REASSIGN",
                            "Chuyển từ " + displayAssignee(previousAssignee) + " sang " + displayAssignee(nextAssignee)
                                    + ". Lý do: " + request.reason().trim(), null);
                });
        return mapBatch(batches.save(batch));
    }

    @Transactional
    public BatchResponse archive(UUID id, long rowVersion) {
        IngestionBatch batch = requireBatch(id);
        checkVersion(batch.getRowVersion(), rowVersion);
        BatchStatistics stats = statisticsFor(id);
        if (stats.totalFiles() > 0 && stats.approved() != stats.totalFiles()) {
            throw ApiException.conflict("Chỉ có thể lưu trữ đợt khi toàn bộ hồ sơ đã được duyệt.");
        }
        batch.setArchivedAt(Instant.now()); batch.setArchivedBy(currentUser.username());
        batch.setUpdatedAt(Instant.now()); batch.setUpdatedBy(currentUser.username());
        return mapBatch(batches.save(batch));
    }

    @Transactional
    public BatchUploadResponse uploadFiles(UUID batchId, BatchSourceType sourceType, List<MultipartFile> files,
                                           List<String> relativePaths) {
        IngestionBatch batch = requireWritableBatch(batchId);
        if (files == null || files.isEmpty()) throw ApiException.badRequest("Vui lòng chọn ít nhất một file PDF.");
        if (documents.countByBatchId(batchId) + files.size() > maxFiles) {
            throw ApiException.badRequest("Đợt hồ sơ vượt quá số lượng file cho phép: " + maxFiles);
        }
        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > maxTotalBytes) throw ApiException.badRequest("Tổng dung lượng tải lên vượt quá giới hạn của một đợt.");
        List<WorkItemSummary> accepted = new ArrayList<>();
        List<UploadError> errors = new ArrayList<>();
        int duplicates = 0;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String path = relativePaths != null && i < relativePaths.size() ? relativePaths.get(i) : file.getOriginalFilename();
            try {
                path = safeRelativePath(path);
                ensurePdf(path, file.getContentType());
                var stored = documentService.uploadPdf(fileName(path), file.getSize(), file.getInputStream(), maxFileBytes);
                if (documents.existsByBatchIdAndDocumentFileChecksumSha256(batchId, stored.checksumSha256())) {
                    documentService.removeUnused(stored.id());
                    duplicates++;
                    continue;
                }
                accepted.add(mapSummary(addDocument(batch, stored.id(), path)));
            } catch (Exception ex) {
                errors.add(new UploadError(path == null ? "file-" + (i + 1) : path, "INVALID_FILE", safeMessage(ex)));
            }
        }
        updateUploadMetadata(batch, sourceType == null ? inferSourceType(files.size(), relativePaths) : sourceType,
                files.size() == 1 ? files.getFirst().getOriginalFilename() : null);
        return new BatchUploadResponse(batchId, accepted.size(), duplicates, errors.size(), accepted, errors,
                statisticsFor(batchId));
    }

    @Transactional
    public BatchUploadResponse uploadZip(UUID batchId, MultipartFile zip) {
        IngestionBatch batch = requireWritableBatch(batchId);
        if (zip == null || zip.isEmpty()) throw ApiException.badRequest("File ZIP rỗng.");
        if (zip.getSize() > maxTotalBytes) throw ApiException.badRequest("File ZIP vượt quá dung lượng cho phép.");
        List<WorkItemSummary> accepted = new ArrayList<>();
        List<UploadError> errors = new ArrayList<>();
        int duplicates = 0;
        long expandedBytes = 0;
        int seenFiles = 0;
        try (ZipInputStream input = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                seenFiles++;
                if (documents.countByBatchId(batchId) + seenFiles > maxFiles) {
                    throw ApiException.badRequest("Đợt hồ sơ vượt quá số lượng file cho phép: " + maxFiles);
                }
                String path;
                try {
                    path = safeRelativePath(entry.getName());
                    ensurePdf(path, null);
                    if (entry.getSize() > maxFileBytes) throw ApiException.badRequest("File vượt quá dung lượng cho phép.");
                    long declared = entry.getSize();
                    var stored = documentService.uploadPdf(fileName(path), declared, nonClosing(input), maxFileBytes);
                    expandedBytes += stored.sizeBytes();
                    if (expandedBytes > maxTotalBytes) {
                        documentService.removeUnused(stored.id());
                        throw ApiException.badRequest("Dung lượng sau giải nén vượt quá giới hạn của một đợt.");
                    }
                    if (documents.existsByBatchIdAndDocumentFileChecksumSha256(batchId, stored.checksumSha256())) {
                        documentService.removeUnused(stored.id());
                        duplicates++;
                    } else {
                        accepted.add(mapSummary(addDocument(batch, stored.id(), path)));
                    }
                } catch (Exception ex) {
                    errors.add(new UploadError(entry.getName(), "INVALID_ZIP_ENTRY", safeMessage(ex)));
                } finally {
                    input.closeEntry();
                }
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Không thể đọc file ZIP hoặc file ZIP đã bị hỏng.");
        }
        updateUploadMetadata(batch, BatchSourceType.ZIP, zip.getOriginalFilename());
        return new BatchUploadResponse(batchId, accepted.size(), duplicates, errors.size(), accepted, errors,
                statisticsFor(batchId));
    }

    @Transactional(readOnly = true)
    public WorkItemPage listWorkItems(WorkItemFilter filter) {
        int page = filter.page() == null ? 0 : Math.max(filter.page(), 0);
        int size = filter.size() == null ? 20 : Math.min(Math.max(filter.size(), 1), 200);
        UUID mine = canViewAll() ? (Boolean.TRUE.equals(filter.mine()) ? currentUserId() : null) : currentUserId();
        Specification<BatchDocument> spec = workItemSpecification(filter, mine);
        Page<BatchDocument> result = documents.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "batch.id", "sequenceNo")));
        return new WorkItemPage(result.getContent().stream().map(this::mapSummary).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public WorkItemDetail getWorkItem(UUID id) {
        BatchDocument item = requireVisibleItem(id, true);
        DynamicRecord record = item.getDynamicRecordId() == null ? null
                : dynamicData.getForVersion(item.getBatch().getTemplateVersion().getId(), item.getDynamicRecordId());
        List<WorkflowHistoryResponse> history = histories.findByBatchDocumentIdOrderByPerformedAtDesc(id).stream()
                .map(this::mapHistory).toList();
        return new WorkItemDetail(mapSummary(item), record, history);
    }

    @Transactional
    public WorkItemDetail saveDraft(UUID id, SaveWorkItemRequest request) {
        BatchDocument item = requireEditableItem(id, request.rowVersion());
        if (!WorkflowPolicy.canSaveDraft(item.getWorkflowStatus())) {
            throw ApiException.conflict("Hồ sơ ở trạng thái hiện tại không thể lưu nháp.");
        }
        DynamicRecord record = saveDynamic(item, request, "DRAFT");
        WorkflowStatus old = item.getWorkflowStatus();
        item.setWorkflowStatus(WorkflowStatus.DRAFT);
        item.setDynamicRecordId(record.id());
        if (item.getStartedAt() == null) item.setStartedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        documents.save(item);
        if (old != WorkflowStatus.DRAFT) addHistory(item, old, WorkflowStatus.DRAFT, "SAVE_DRAFT", null, record);
        return detail(item, record);
    }

    @Transactional
    public WorkItemDetail submit(UUID id, SaveWorkItemRequest request) {
        BatchDocument item = requireEditableItem(id, request.rowVersion());
        if (!WorkflowPolicy.canSubmit(item.getWorkflowStatus())) {
            throw ApiException.conflict("Chỉ hồ sơ chưa nhập, nháp hoặc bị trả lại mới có thể gửi duyệt.");
        }
        DynamicRecord record = saveDynamic(item, request, "COMPLETED");
        WorkflowStatus old = item.getWorkflowStatus(); Instant now = Instant.now();
        item.setWorkflowStatus(WorkflowStatus.PENDING_APPROVAL); item.setDynamicRecordId(record.id());
        item.setSubmittedAt(now); item.setSubmittedBy(currentUser.username());
        item.setRejectionReason(null); item.setRejectedAt(null); item.setRejectedBy(null);
        if (item.getStartedAt() == null) item.setStartedAt(now);
        item.setUpdatedAt(now); documents.save(item);
        addHistory(item, old, WorkflowStatus.PENDING_APPROVAL, "SUBMIT", null, record);
        return detail(item, record);
    }

    @Transactional
    public WorkItemDetail approve(UUID id, WorkflowActionRequest request) {
        BatchDocument item = requireItem(id, false); checkVersion(item.getRowVersion(), request.rowVersion());
        if (!WorkflowPolicy.canApproveOrReject(item.getWorkflowStatus())) {
            throw ApiException.conflict("Chỉ hồ sơ đang chờ duyệt mới có thể phê duyệt.");
        }
        if (currentUser.username().equalsIgnoreCase(item.getSubmittedBy())) {
            throw ApiException.conflict("Người gửi duyệt không được tự phê duyệt hồ sơ của mình.");
        }
        Instant now = Instant.now(); item.setWorkflowStatus(WorkflowStatus.APPROVED);
        item.setApprovedAt(now); item.setApprovedBy(currentUser.username()); item.setUpdatedAt(now);
        documents.save(item); addHistory(item, WorkflowStatus.PENDING_APPROVAL, WorkflowStatus.APPROVED,
                "APPROVE", blankToNull(request.reason()), currentDynamic(item));
        return detail(item, currentDynamic(item));
    }

    @Transactional
    public WorkItemDetail reject(UUID id, RejectWorkItemRequest request) {
        BatchDocument item = requireItem(id, false); checkVersion(item.getRowVersion(), request.rowVersion());
        if (!WorkflowPolicy.canApproveOrReject(item.getWorkflowStatus())) {
            throw ApiException.conflict("Chỉ hồ sơ đang chờ duyệt mới có thể trả lại.");
        }
        DynamicRecord current = currentDynamic(item);
        DynamicRecord draft = dynamicData.updateForVersion(item.getBatch().getTemplateVersion().getId(), current.id(),
                new UpdateRecordRequest(current.rowVersion(), item.getDocumentFile().getId(), "DRAFT", Map.of()));
        Instant now = Instant.now(); item.setWorkflowStatus(WorkflowStatus.REJECTED);
        item.setRejectedAt(now); item.setRejectedBy(currentUser.username());
        item.setRejectionReason(request.rejectionReason().trim()); item.setUpdatedAt(now);
        documents.save(item); addHistory(item, WorkflowStatus.PENDING_APPROVAL, WorkflowStatus.REJECTED,
                "REJECT", item.getRejectionReason(), draft);
        return detail(item, draft);
    }

    @Transactional
    public WorkItemDetail reopen(UUID id, WorkflowActionRequest request) {
        BatchDocument item = requireItem(id, false); checkVersion(item.getRowVersion(), request.rowVersion());
        if (!WorkflowPolicy.canReopen(item.getWorkflowStatus())) {
            throw ApiException.conflict("Chỉ hồ sơ đã duyệt mới có thể thu hồi phê duyệt.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("Phải nhập lý do thu hồi phê duyệt.");
        }
        DynamicRecord current = currentDynamic(item);
        DynamicRecord draft = dynamicData.updateForVersion(item.getBatch().getTemplateVersion().getId(), current.id(),
                new UpdateRecordRequest(current.rowVersion(), item.getDocumentFile().getId(), "DRAFT", Map.of()));
        item.setWorkflowStatus(WorkflowStatus.DRAFT); item.setApprovedAt(null); item.setApprovedBy(null);
        item.setRejectionReason(request.reason().trim()); item.setUpdatedAt(Instant.now());
        documents.save(item); addHistory(item, WorkflowStatus.APPROVED, WorkflowStatus.DRAFT,
                "REOPEN", request.reason().trim(), draft);
        return detail(item, draft);
    }

    @Transactional
    public void delete(UUID id, long rowVersion) {
        BatchDocument item = requireItem(id, false); checkVersion(item.getRowVersion(), rowVersion);
        assertAssignedOwner(item);
        if (!WorkflowPolicy.canDelete(item.getWorkflowStatus())) {
            throw ApiException.conflict("Hồ sơ đã duyệt phải được thu hồi phê duyệt trước khi xóa.");
        }
        item.setPreviousStatus(item.getWorkflowStatus()); item.setDeleted(true);
        item.setDeletedAt(Instant.now()); item.setDeletedBy(currentUser.username()); item.setUpdatedAt(Instant.now());
        documents.save(item); addHistory(item, item.getWorkflowStatus(), item.getWorkflowStatus(), "DELETE", null, currentDynamicOrNull(item));
    }

    @Transactional
    public WorkItemDetail restore(UUID id, WorkflowActionRequest request) {
        BatchDocument item = requireItem(id, true); checkVersion(item.getRowVersion(), request.rowVersion());
        if (!Boolean.TRUE.equals(item.getDeleted())) throw ApiException.conflict("Hồ sơ không nằm trong thùng rác.");
        WorkflowStatus restored = item.getPreviousStatus() == null ? WorkflowStatus.DRAFT : item.getPreviousStatus();
        item.setDeleted(false); item.setDeletedAt(null); item.setDeletedBy(null); item.setPreviousStatus(null);
        item.setWorkflowStatus(restored); item.setUpdatedAt(Instant.now()); documents.save(item);
        addHistory(item, restored, restored, "RESTORE", blankToNull(request.reason()), currentDynamicOrNull(item));
        return detail(item, currentDynamicOrNull(item));
    }

    @Transactional(readOnly = true)
    public WorkItemSummary adjacent(UUID id, boolean next) {
        BatchDocument item = requireVisibleItem(id, false);
        Optional<BatchDocument> adjacent;
        if (canViewAll()) {
            adjacent = next
                    ? documents.findFirstByBatchIdAndDeletedFalseAndSequenceNoGreaterThanOrderBySequenceNoAsc(item.getBatch().getId(), item.getSequenceNo())
                    : documents.findFirstByBatchIdAndDeletedFalseAndSequenceNoLessThanOrderBySequenceNoDesc(item.getBatch().getId(), item.getSequenceNo());
        } else {
            UUID actorId = currentUserId();
            adjacent = next
                    ? documents.findFirstByBatchIdAndAssignedUserIdAndDeletedFalseAndSequenceNoGreaterThanOrderBySequenceNoAsc(item.getBatch().getId(), actorId, item.getSequenceNo())
                    : documents.findFirstByBatchIdAndAssignedUserIdAndDeletedFalseAndSequenceNoLessThanOrderBySequenceNoDesc(item.getBatch().getId(), actorId, item.getSequenceNo());
        }
        return adjacent
                .map(this::mapSummary).orElse(null);
    }

    private Specification<BatchDocument> workItemSpecification(WorkItemFilter f, UUID mine) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.batchId() != null) p.add(cb.equal(root.get("batch").get("id"), f.batchId()));
            if (f.status() != null) p.add(cb.equal(root.get("workflowStatus"), f.status()));
            if (f.statuses() != null && !f.statuses().isEmpty()) p.add(root.get("workflowStatus").in(f.statuses()));
            UUID assignee = mine != null ? mine : f.assignedUserId();
            if (assignee != null) p.add(cb.equal(root.get("assignedUserId"), assignee));
            if (f.keyword() != null && !f.keyword().isBlank()) {
                String value = "%" + f.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("displayName")), value),
                        cb.like(cb.lower(root.get("relativePath")), value),
                        cb.like(cb.lower(root.get("batch").get("batchCode")), value)));
            }
            p.add(cb.equal(root.get("deleted"), Boolean.TRUE.equals(f.deleted())));
            if (f.fromDate() != null) p.add(cb.greaterThanOrEqualTo(root.get("updatedAt"),
                    f.fromDate().atStartOfDay(BUSINESS_ZONE).toInstant()));
            if (f.toDate() != null) p.add(cb.lessThan(root.get("updatedAt"),
                    f.toDate().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private DynamicRecord saveDynamic(BatchDocument item, SaveWorkItemRequest request, String status) {
        UUID versionId = item.getBatch().getTemplateVersion().getId();
        UUID sourceDocumentId = item.getDocumentFile() == null ? null : item.getDocumentFile().getId();
        if (item.getDynamicRecordId() == null) {
            return dynamicData.createForVersion(versionId, new CreateRecordRequest(sourceDocumentId, status, request.data()));
        }
        DynamicRecord current = dynamicData.getForVersion(versionId, item.getDynamicRecordId());
        long version = request.dynamicRowVersion() == null ? current.rowVersion() : request.dynamicRowVersion();
        return dynamicData.updateForVersion(versionId, current.id(),
                new UpdateRecordRequest(version, sourceDocumentId, status, request.data()));
    }

    private BatchDocument addDocument(IngestionBatch batch, UUID documentId, String relativePath) {
        BatchDocument item = new BatchDocument(); Instant now = Instant.now();
        item.setId(UUID.randomUUID()); item.setBatch(batch); item.setDocumentFile(documentFiles.getReferenceById(documentId));
        item.setSequenceNo(Math.toIntExact(documents.countByBatchId(batch.getId()) + 1));
        item.setRelativePath(relativePath); item.setDisplayName(fileName(relativePath));
        item.setWorkflowStatus(WorkflowStatus.UNPROCESSED); item.setAssignedUserId(batch.getAssignedUserId());
        item.setCreatedAt(now); item.setUpdatedAt(now);
        item = documents.save(item);
        addHistory(item, null, WorkflowStatus.UNPROCESSED, "UPLOAD", null, null);
        return item;
    }

    private void updateUploadMetadata(IngestionBatch batch, BatchSourceType type, String originalName) {
        batch.setSourceType(type); if (originalName != null) batch.setOriginalName(originalName);
        batch.setUpdatedAt(Instant.now()); batch.setUpdatedBy(currentUser.username()); batches.save(batch);
    }

    private BatchDocument requireEditableItem(UUID id, long expectedVersion) {
        BatchDocument item = requireItem(id, false); checkVersion(item.getRowVersion(), expectedVersion);
        if (item.getBatch().getArchivedAt() != null) throw ApiException.conflict("Đợt hồ sơ đã được lưu trữ.");
        assertAssignedOwner(item);
        return item;
    }

    private void assertAssignedOwner(BatchDocument item) {
        if (!securityEnabled) return;
        UUID actorId = currentUserId();
        if (item.getAssignedUserId() == null) {
            throw ApiException.forbidden("Hồ sơ chưa được giao cho người nhập liệu. Hãy giao việc trước khi cập nhật dữ liệu.");
        }
        if (!item.getAssignedUserId().equals(actorId)) {
            throw ApiException.forbidden("Hồ sơ đang được giao cho người nhập liệu khác. Bạn chỉ có thể xem hoặc chuyển giao công việc.");
        }
    }

    private UUID currentUserId() {
        return security.findUserByUsername(currentUser.username()).map(SecurityRepository.UserRow::id)
                .orElseThrow(() -> ApiException.forbidden("Không xác định được tài khoản hiện tại."));
    }

    private void validateAssignee(UUID id) {
        if (id == null) return;
        SecurityRepository.UserRow user = security.findUserById(id)
                .orElseThrow(() -> ApiException.badRequest("Người được giao không tồn tại."));
        if (!"ACTIVE".equals(user.status())) throw ApiException.badRequest("Người được giao đang không hoạt động.");
        if (securityEnabled && (!security.hasFunction(user.username(), "RECORD_UPDATE")
                || !security.hasFunction(user.username(), "RECORD_SUBMIT"))) {
            throw ApiException.badRequest("Chỉ có thể giao đợt cho tài khoản có vai trò nhập liệu.");
        }
    }

    private IngestionBatch requireBatch(UUID id) {
        return batches.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt hồ sơ."));
    }

    private IngestionBatch requireVisibleBatch(UUID id) {
        IngestionBatch batch = requireBatch(id);
        if (!canViewAll() && !Objects.equals(batch.getAssignedUserId(), currentUserId())) {
            throw ApiException.notFound("Không tìm thấy đợt hồ sơ.");
        }
        return batch;
    }

    private IngestionBatch requireWritableBatch(UUID id) {
        IngestionBatch batch = requireBatch(id);
        if (!canViewAll() && !Objects.equals(batch.getAssignedUserId(), currentUserId())) {
            throw ApiException.notFound("Không tìm thấy đợt hồ sơ.");
        }
        if (batch.getArchivedAt() != null) throw ApiException.conflict("Đợt hồ sơ đã được lưu trữ.");
        return batch;
    }

    private UUID resolveAssigneeForNewBatch(UUID requestedAssigneeId) {
        if (!securityEnabled || security.hasFunction(currentUser.username(), "BATCH_ASSIGN")) {
            validateAssignee(requestedAssigneeId);
            return requestedAssigneeId;
        }
        UUID actorId = currentUserId();
        if (requestedAssigneeId != null && !requestedAssigneeId.equals(actorId)) {
            throw ApiException.forbidden("Bạn chỉ có thể tạo đợt hồ sơ cho chính mình.");
        }
        return actorId;
    }

    private BatchDocument requireItem(UUID id, boolean includeDeleted) {
        BatchDocument item = documents.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hồ sơ trong đợt."));
        if (!includeDeleted && Boolean.TRUE.equals(item.getDeleted())) throw ApiException.notFound("Không tìm thấy hồ sơ trong đợt.");
        return item;
    }

    private BatchDocument requireVisibleItem(UUID id, boolean includeDeleted) {
        BatchDocument item = requireItem(id, includeDeleted);
        if (!canViewAll() && !Objects.equals(item.getAssignedUserId(), currentUserId())) {
            throw ApiException.notFound("Không tìm thấy hồ sơ trong đợt.");
        }
        return item;
    }

    private boolean canViewAll() {
        return !securityEnabled
                || security.hasFunction(currentUser.username(), "BATCH_ASSIGN")
                || security.hasFunction(currentUser.username(), "RECORD_APPROVE");
    }

    private BatchStatistics statisticsFor(UUID batchId) {
        EnumMap<WorkflowStatus, Long> counts = new EnumMap<>(WorkflowStatus.class);
        documents.countStatuses(batchId).forEach(row -> counts.put((WorkflowStatus) row[0], (Long) row[1]));
        return new BatchStatistics(documents.countByBatchIdAndDeletedFalse(batchId),
                counts.getOrDefault(WorkflowStatus.UNPROCESSED, 0L), counts.getOrDefault(WorkflowStatus.DRAFT, 0L),
                counts.getOrDefault(WorkflowStatus.PENDING_APPROVAL, 0L), counts.getOrDefault(WorkflowStatus.APPROVED, 0L),
                counts.getOrDefault(WorkflowStatus.REJECTED, 0L), documents.countDeleted(batchId));
    }

    private BatchResponse mapBatch(IngestionBatch batch) {
        BatchStatistics stats = statisticsFor(batch.getId());
        TemplateVersion v = batch.getTemplateVersion();
        String status = batch.getArchivedAt() != null ? "ARCHIVED"
                : stats.totalFiles() == 0 ? "EMPTY"
                : stats.approved() == stats.totalFiles() ? "COMPLETED"
                : stats.unprocessed() == stats.totalFiles() ? "NEW"
                : stats.pendingApproval() > 0 && stats.unprocessed() + stats.draft() + stats.rejected() == 0 ? "PENDING_APPROVAL"
                : "IN_PROGRESS";
        return new BatchResponse(batch.getId(), batch.getBatchCode(), batch.getBatchName(), v.getTemplate().getId(),
                v.getTemplate().getCode(), v.getTemplate().getName(), v.getId(), v.getVersionNo(), batch.getSourceType(),
                batch.getOriginalName(), batch.getAssignedUserId(), userName(batch.getAssignedUserId()), batch.getDescription(),
                status, stats, batch.getRowVersion(), batch.getArchivedAt(), batch.getCreatedAt(), batch.getCreatedBy(), batch.getUpdatedAt());
    }

    private WorkItemSummary mapSummary(BatchDocument item) {
        TemplateVersion v = item.getBatch().getTemplateVersion();
        UUID documentId = item.getDocumentFile() == null ? null : item.getDocumentFile().getId();
        return new WorkItemSummary(item.getId(), item.getBatch().getId(), item.getBatch().getBatchCode(),
                item.getBatch().getBatchName(), v.getId(), v.getTemplate().getCode(), v.getVersionNo(), item.getSequenceNo(),
                documentId, documentId == null ? null : "/api/v1/documents/" + documentId + "/content",
                item.getRelativePath(), item.getDisplayName(), item.getWorkflowStatus(), item.getAssignedUserId(),
                userName(item.getAssignedUserId()), item.getDynamicRecordId(), item.getRejectionReason(),
                Boolean.TRUE.equals(item.getDeleted()), item.getRowVersion(), item.getStartedAt(), item.getSubmittedAt(),
                item.getSubmittedBy(), item.getApprovedAt(), item.getApprovedBy(), item.getUpdatedAt());
    }

    private String userName(UUID id) {
        return id == null ? null : security.findUserById(id).map(SecurityRepository.UserRow::displayName).orElse(null);
    }

    private WorkItemDetail detail(BatchDocument item, DynamicRecord record) {
        return new WorkItemDetail(mapSummary(item), record, histories.findByBatchDocumentIdOrderByPerformedAtDesc(item.getId())
                .stream().map(this::mapHistory).toList());
    }

    private WorkflowHistoryResponse mapHistory(RecordWorkflowHistory h) {
        return new WorkflowHistoryResponse(h.getId(), h.getFromStatus(), h.getToStatus(), h.getAction(), h.getComment(),
                h.getPerformedBy(), h.getPerformedAt());
    }

    private void addHistory(BatchDocument item, WorkflowStatus from, WorkflowStatus to, String action,
                            String comment, DynamicRecord record) {
        RecordWorkflowHistory history = new RecordWorkflowHistory(); history.setId(UUID.randomUUID());
        history.setBatchDocument(item); history.setFromStatus(from); history.setToStatus(to); history.setAction(action);
        history.setComment(comment); history.setPerformedBy(currentUser.username()); history.setPerformedAt(Instant.now());
        if (record != null) history.setSnapshotJson(record.data());
        histories.save(history);
    }

    private DynamicRecord currentDynamic(BatchDocument item) {
        if (item.getDynamicRecordId() == null) throw ApiException.conflict("Hồ sơ chưa có dữ liệu nhập.");
        return dynamicData.getForVersion(item.getBatch().getTemplateVersion().getId(), item.getDynamicRecordId());
    }

    private DynamicRecord currentDynamicOrNull(BatchDocument item) {
        return item.getDynamicRecordId() == null ? null : currentDynamic(item);
    }

    private String nextBatchCode() {
        String prefix = "LO-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(BUSINESS_ZONE).format(Instant.now());
        String code;
        do { code = prefix + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT); }
        while (batches.existsByBatchCode(code));
        return code;
    }

    private void checkVersion(Long actual, Long expected) {
        if (expected == null || !Objects.equals(actual, expected)) {
            throw ApiException.conflict("Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại trước khi tiếp tục.");
        }
    }

    private String safeRelativePath(String value) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("Tên file không hợp lệ.");
        String normalized = value.replace('\\', '/').replaceAll("^/+", "");
        if (normalized.length() > 1000 || normalized.indexOf('\0') >= 0) throw ApiException.badRequest("Đường dẫn file không hợp lệ.");
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw ApiException.badRequest("Đường dẫn file không hợp lệ.");
            }
        }
        return normalized;
    }

    private void ensurePdf(String path, String contentType) {
        if (!path.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw ApiException.badRequest("Chỉ hỗ trợ file PDF.");
        if (contentType != null && !Set.of("application/pdf", "application/octet-stream").contains(contentType.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("Định dạng file không phải PDF.");
        }
    }

    private String fileName(String path) {
        int slash = path.lastIndexOf('/'); return slash < 0 ? path : path.substring(slash + 1);
    }

    private BatchSourceType inferSourceType(int count, List<String> relativePaths) {
        if (relativePaths != null && relativePaths.stream().filter(Objects::nonNull).anyMatch(path -> path.contains("/") || path.contains("\\"))) {
            return BatchSourceType.FOLDER;
        }
        return count == 1 ? BatchSourceType.SINGLE_FILE : BatchSourceType.MULTI_FILE;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String displayAssignee(String value) { return value == null || value.isBlank() ? "chưa giao" : value; }
    private String safeMessage(Exception ex) {
        String value = ex.getMessage(); return value == null || value.isBlank() ? "File không hợp lệ." : value;
    }

    private InputStream nonClosing(InputStream input) {
        return new java.io.FilterInputStream(input) {
            @Override public void close() {}
        };
    }
}
