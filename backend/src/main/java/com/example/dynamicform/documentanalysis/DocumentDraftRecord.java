package com.example.dynamicform.documentanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_draft_record", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class DocumentDraftRecord {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", unique = true)
    private DocumentAnalysis analysis;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode schemaSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode data;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 100) private String createdBy;
    @Column(nullable = false) private Instant updatedAt;
    @Column(nullable = false, length = 100) private String updatedBy;
    @Version private Long rowVersion;
}
