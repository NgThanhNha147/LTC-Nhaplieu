package com.example.dynamicform.template.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "form_template", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class FormTemplate {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String code;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TemplateStatus status;
    private Integer currentVersion;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 100) private String createdBy;
    @Column(nullable = false) private Instant updatedAt;
    @Column(nullable = false, length = 100) private String updatedBy;
}
