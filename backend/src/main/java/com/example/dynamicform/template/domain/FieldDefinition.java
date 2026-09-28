package com.example.dynamicform.template.domain;

import com.example.dynamicform.lookup.domain.LookupSource;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "field_definition", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class FieldDefinition {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "template_version_id") private TemplateVersion templateVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_id") private FieldGroup group;
    @Column(nullable = false, length = 100) private String fieldCode;
    @Column(nullable = false, length = 63) private String columnName;
    @Column(nullable = false) private String label;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private FieldDataType dataType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ComponentType componentType;
    @Column(columnDefinition = "text") private String description;
    private String placeholder;
    @Column(nullable = false) private Boolean required;
    @Column(nullable = false) private Boolean uniqueValue;
    @Column(nullable = false) private Boolean businessKey;
    @Column(nullable = false) private Boolean indexed;
    @Column(nullable = false) private Boolean searchable;
    @Column(nullable = false) private Boolean sortable;
    @Column(nullable = false) private Boolean exportable;
    private Integer maxLength;
    private Integer precisionValue;
    private Integer scaleValue;
    @Column(columnDefinition = "text") private String defaultValue;
    @Column(nullable = false) private Integer displayOrder;
    @Column(nullable = false) private Integer gridSpan;
    @Column(nullable = false) private Boolean readOnly;
    @Column(nullable = false) private Boolean hidden;
    @Column(columnDefinition = "text") private String helpText;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lookup_source_id") private LookupSource lookupSource;
}
