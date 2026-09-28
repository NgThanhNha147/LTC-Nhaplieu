package com.example.dynamicform.template.repository;

import com.example.dynamicform.template.domain.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ValidationRuleRepository extends JpaRepository<ValidationRule, UUID> {
    List<ValidationRule> findByFieldIdOrderByDisplayOrderAsc(UUID fieldId);
    List<ValidationRule> findByFieldIdIn(Collection<UUID> fieldIds);
    void deleteByFieldId(UUID fieldId);
}
