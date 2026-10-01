package com.example.dynamicform.batch;

import com.example.dynamicform.document.DocumentFile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batch_document", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class BatchDocument {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private IngestionBatch batch;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_file_id")
    private DocumentFile documentFile;
    @Column(nullable = false) private Integer sequenceNo;
    @Column(length = 1000) private String relativePath;
    @Column(nullable = false, length = 500) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private WorkflowStatus workflowStatus;
    private UUID assignedUserId;
    private UUID dynamicRecordId;
    private Instant startedAt;
    private Instant submittedAt;
    @Column(length = 100) private String submittedBy;
    private Instant approvedAt;
    @Column(length = 100) private String approvedBy;
    private Instant rejectedAt;
    @Column(length = 100) private String rejectedBy;
    @Column(length = 2000) private String rejectionReason;
    @Column(nullable = false) private Boolean deleted = false;
    private Instant deletedAt;
    @Column(length = 100) private String deletedBy;
    @Enumerated(EnumType.STRING) @Column(length = 30) private WorkflowStatus previousStatus;
    @Version private Long rowVersion;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}
