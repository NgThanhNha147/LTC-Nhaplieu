package com.example.dynamicform.template.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "template_rule", schema = "app_meta")
@Getter
@Setter
@NoArgsConstructor
public class TemplateRule {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id")
    private TemplateVersion templateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FormRuleType ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> ruleConfig;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private Integer displayOrder;
}
