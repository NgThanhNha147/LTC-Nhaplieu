package com.example.dynamicform.batch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "record_workflow_history", schema = "app_audit")
@Getter @Setter @NoArgsConstructor
public class RecordWorkflowHistory {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_document_id")
    private BatchDocument batchDocument;
    @Enumerated(EnumType.STRING) @Column(length = 30) private WorkflowStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private WorkflowStatus toStatus;
    @Column(nullable = false, length = 40) private String action;
    @Column(length = 2000) private String comment;
    @Column(nullable = false, length = 100) private String performedBy;
    @Column(nullable = false) private Instant performedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, Object> snapshotJson;
}
