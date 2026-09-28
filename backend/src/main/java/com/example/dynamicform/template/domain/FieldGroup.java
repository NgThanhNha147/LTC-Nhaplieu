package com.example.dynamicform.template.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "field_group", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class FieldGroup {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "template_version_id") private TemplateVersion templateVersion;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false) private String label;
    @Column(columnDefinition = "text") private String description;
    @Column(nullable = false) private Integer displayOrder;
    @Column(nullable = false) private Integer columnCount;
    @Column(nullable = false) private Boolean collapsible;
    @Column(nullable = false) private Boolean defaultCollapsed;
    @Column(nullable = false) private Boolean repeatable;
}
