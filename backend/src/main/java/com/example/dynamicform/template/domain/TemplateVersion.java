package com.example.dynamicform.template.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "template_version", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class TemplateVersion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "template_id") private FormTemplate template;
    @Column(nullable = false) private Integer versionNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private VersionStatus status;
    @Column(length = 63) private String physicalSchema;
    @Column(length = 63) private String physicalTable;
    @Column(length = 80) private String configHash;
    private Instant generatedAt;
    private Instant publishedAt;
    @Column(nullable = false, length = 30) private String storageStatus = "ACTIVE";
    private Instant purgedAt;
    @Column(length = 100) private String purgedBy;
    @Column(length = 500) private String purgeReason;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 100) private String createdBy;
}
