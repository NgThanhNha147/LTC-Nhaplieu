package com.example.dynamicform.template.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "validation_rule", schema = "app_meta")
@Getter @Setter @NoArgsConstructor
public class ValidationRule {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "field_id") private FieldDefinition field;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RuleType ruleType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, Object> ruleConfig;
    @Column(length = 500) private String errorMessage;
    @Column(nullable = false) private Integer displayOrder;
}
