package com.example.dynamicform.template.repository;

import com.example.dynamicform.template.domain.TemplateRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateRuleRepository extends JpaRepository<TemplateRule, UUID> {
    List<TemplateRule> findByTemplateVersionIdOrderByDisplayOrderAsc(UUID versionId);
}
