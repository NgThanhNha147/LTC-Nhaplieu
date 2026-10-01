package com.example.dynamicform.document;

import com.example.dynamicform.batch.BatchDocumentRepository;
import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.security.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAuthorizationTest {
    @Mock DocumentRepository documents;
    @Mock BatchDocumentRepository batchDocuments;
    @Mock SecurityRepository security;
    @Mock CurrentUser currentUser;
    @Mock DocumentOcrService ocrService;
    @Mock FileSignatureValidator signatures;
    @InjectMocks DocumentService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void enableSecurity() {
        ReflectionTestUtils.setField(service, "securityEnabled", true);
        when(currentUser.username()).thenReturn("entry.user");
    }

    @Test
    void assignedDataEntryCanViewDocument() {
        UUID documentId = UUID.randomUUID();
        when(security.findUserByUsername("entry.user")).thenReturn(Optional.of(user()));
        when(batchDocuments.existsByDocumentFileIdAndDeletedFalseAndAssignedUserId(documentId, userId)).thenReturn(true);

        assertThatCode(() -> service.assertCanView(documentId)).doesNotThrowAnyException();
    }

    @Test
    void dataEntryCannotViewUnassignedDocument() {
        UUID documentId = UUID.randomUUID();
        when(security.findUserByUsername("entry.user")).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> service.assertCanView(documentId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Không tìm thấy tài liệu hoặc tài liệu không thuộc hồ sơ được giao.");
    }

    @Test
    void administratorCanViewAnyDocument() {
        UUID documentId = UUID.randomUUID();
        when(security.hasFunction("entry.user", "BATCH_ASSIGN")).thenReturn(true);

        assertThatCode(() -> service.assertCanView(documentId)).doesNotThrowAnyException();
    }

    private SecurityRepository.UserRow user() {
        return new SecurityRepository.UserRow(userId, "entry.user", "hash", "Entry User", null,
                "ACTIVE", 0, null, false, 0);
    }
}

