package com.example.dynamicform.documentanalysis;

import com.example.dynamicform.document.DocumentFile;
import com.example.dynamicform.template.domain.TemplateVersion;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_analysis", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class DocumentAnalysis {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_file_id")
    private DocumentFile documentFile;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private AnalysisStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private DocumentSourceKind sourceKind;
    @Column(length = 255) private String documentType;
    @Column(precision = 5, scale = 4) private BigDecimal confidence;
    @Column(columnDefinition = "text") private String extractedText;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode suggestedSchema;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private JsonNode reviewedSchema;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_template_version_id")
    private TemplateVersion matchedTemplateVersion;
    @Column(precision = 5, scale = 4) private BigDecimal matchConfidence;
    @Column(length = 2000) private String errorMessage;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 100) private String createdBy;
    @Column(nullable = false) private Instant updatedAt;
    @Column(nullable = false, length = 100) private String updatedBy;
    @Version private Long rowVersion;
}
