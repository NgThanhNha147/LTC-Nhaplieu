package com.example.dynamicform.document;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_file", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class DocumentFile {
    @Id private UUID id;
    @Column(nullable = false, length = 500) private String originalName;
    @Column(nullable = false, length = 500) private String storedName;
    @Column(length = 150) private String contentType;
    @Column(nullable = false) private Long sizeBytes;
    @Column(nullable = false, length = 64) private String checksumSha256;
    @Column(nullable = false, length = 1000) private String storagePath;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 100) private String createdBy;
    @Column(nullable = false, length = 30) private String ocrStatus = "PENDING";
    @Column(length = 1000) private String ocrTextPath;
    @Column(length = 1000) private String ocrPdfPath;
    @Column(length = 40) private String ocrEngine;
    @Column(length = 2000) private String ocrError;
    private Instant ocrStartedAt;
    private Instant ocrCompletedAt;
}
