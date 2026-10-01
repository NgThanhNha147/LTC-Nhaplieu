package com.example.dynamicform.batch;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.security.SecurityRepository;
import com.example.dynamicform.template.domain.FormTemplate;
import com.example.dynamicform.template.domain.TemplateVersion;
import com.example.dynamicform.template.domain.VersionStatus;
import com.example.dynamicform.template.repository.TemplateVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchVisibilityTest {
    @Mock IngestionBatchRepository batches;
    @Mock BatchDocumentRepository documents;
    @Mock TemplateVersionRepository versions;
    @Mock SecurityRepository security;
    @Mock CurrentUser currentUser;
    @InjectMocks BatchWorkflowService service;

    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void enableSecurity() {
        ReflectionTestUtils.setField(service, "securityEnabled", true);
        when(currentUser.username()).thenReturn("entry.user");
        lenient().when(security.hasFunction("entry.user", "BATCH_ASSIGN")).thenReturn(false);
        lenient().when(security.hasFunction("entry.user", "RECORD_APPROVE")).thenReturn(false);
        lenient().when(security.findUserByUsername("entry.user")).thenReturn(Optional.of(new SecurityRepository.UserRow(
                actorId, "entry.user", "hash", "Entry User", null, "ACTIVE", 0,
                null, false, 0)));
    }

    @Test
    void dataEntryCannotOpenBatchAssignedToAnotherUser() {
        UUID batchId = UUID.randomUUID();
        IngestionBatch batch = new IngestionBatch();
        batch.setId(batchId); batch.setAssignedUserId(UUID.randomUUID());
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.getBatch(batchId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Không tìm thấy đợt hồ sơ.");
    }

    @Test
    void dataEntryCannotOpenWorkItemAssignedToAnotherUser() {
        UUID itemId = UUID.randomUUID();
        BatchDocument item = new BatchDocument();
        item.setId(itemId); item.setAssignedUserId(UUID.randomUUID()); item.setDeleted(false);
        when(documents.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.getWorkItem(itemId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Không tìm thấy hồ sơ trong đợt.");
    }

    @Test
    void dataEntryCreatesBatchAssignedToSelf() {
        UUID versionId = UUID.randomUUID();
        when(versions.findById(versionId)).thenReturn(Optional.of(publishedVersion(versionId)));
        when(batches.save(any(IngestionBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new BatchDtos.CreateBatchRequest(
                "Đợt nhập liệu tháng 10", versionId, null, null));

        assertThat(created.assignedUserId()).isEqualTo(actorId);
    }

    @Test
    void dataEntryCannotCreateBatchForAnotherUser() {
        UUID versionId = UUID.randomUUID();
        when(versions.findById(versionId)).thenReturn(Optional.of(publishedVersion(versionId)));

        assertThatThrownBy(() -> service.create(new BatchDtos.CreateBatchRequest(
                "Đợt không hợp lệ", versionId, UUID.randomUUID(), null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Bạn chỉ có thể tạo đợt hồ sơ cho chính mình.");
    }

    @Test
    void dataEntryCannotUploadToBatchAssignedToAnotherUser() {
        UUID batchId = UUID.randomUUID();
        IngestionBatch batch = new IngestionBatch();
        batch.setId(batchId);
        batch.setAssignedUserId(UUID.randomUUID());
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.uploadFiles(batchId, BatchSourceType.MULTI_FILE, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Không tìm thấy đợt hồ sơ.");
    }

    @Test
    void managerCanViewButCannotEditWorkAssignedToAnotherUser() {
        UUID managerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(currentUser.username()).thenReturn("manager.user");
        when(security.findUserByUsername("manager.user")).thenReturn(Optional.of(new SecurityRepository.UserRow(
                managerId, "manager.user", "hash", "Manager", null, "ACTIVE", 0,
                null, false, 0)));

        IngestionBatch batch = new IngestionBatch();
        batch.setId(UUID.randomUUID());
        BatchDocument item = new BatchDocument();
        item.setId(itemId); item.setBatch(batch); item.setAssignedUserId(UUID.randomUUID());
        item.setDeleted(false); item.setWorkflowStatus(WorkflowStatus.DRAFT); item.setRowVersion(0L);
        when(documents.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.saveDraft(itemId,
                new BatchDtos.SaveWorkItemRequest(0L, null, Map.of())))
                .isInstanceOf(ApiException.class)
                .hasMessage("Hồ sơ đang được giao cho người nhập liệu khác. Bạn chỉ có thể xem hoặc chuyển giao công việc.");
    }

    @Test
    void managerCannotDeleteWorkAssignedToAnotherUser() {
        UUID managerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(currentUser.username()).thenReturn("manager.user");
        when(security.findUserByUsername("manager.user")).thenReturn(Optional.of(new SecurityRepository.UserRow(
                managerId, "manager.user", "hash", "Manager", null, "ACTIVE", 0,
                null, false, 0)));

        BatchDocument item = new BatchDocument();
        item.setId(itemId); item.setAssignedUserId(UUID.randomUUID()); item.setDeleted(false);
        item.setWorkflowStatus(WorkflowStatus.DRAFT); item.setRowVersion(0L);
        when(documents.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.delete(itemId, 0L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Hồ sơ đang được giao cho người nhập liệu khác. Bạn chỉ có thể xem hoặc chuyển giao công việc.");
    }

    private TemplateVersion publishedVersion(UUID versionId) {
        FormTemplate template = new FormTemplate();
        template.setId(UUID.randomUUID());
        template.setCode("LAND_CERTIFICATE");
        template.setName("Giấy chứng nhận quyền sử dụng đất");
        TemplateVersion version = new TemplateVersion();
        version.setId(versionId);
        version.setTemplate(template);
        version.setVersionNo(1);
        version.setStatus(VersionStatus.PUBLISHED);
        version.setPhysicalSchema("generated_data");
        version.setPhysicalTable("land_certificate_v1");
        version.setStorageStatus("ACTIVE");
        return version;
    }
}
