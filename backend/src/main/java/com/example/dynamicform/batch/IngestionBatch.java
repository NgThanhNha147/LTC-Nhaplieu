package com.example.dynamicform.batch;

import com.example.dynamicform.template.domain.TemplateVersion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_batch", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class IngestionBatch {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 40) private String batchCode;
    @Column(nullable = false, length = 255) private String batchName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id")
    private TemplateVersion templateVersion;
    @Enumerated(EnumType.STRING) @Column(length = 30) private BatchSourceType sourceType;
    @Column(length = 500) private String originalName;
    private UUID assignedUserId;
    @Column(columnDefinition = "text") private String description;
    private Instant archivedAt;
    @Column(length = 100) private String archivedBy;
    @Version private Long rowVersion;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 100) private String createdBy;
    @Column(nullable = false) private Instant updatedAt;
    @Column(nullable = false, length = 100) private String updatedBy;
}
