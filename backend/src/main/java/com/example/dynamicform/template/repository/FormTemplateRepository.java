package com.example.dynamicform.template.repository;

import com.example.dynamicform.template.domain.FormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, UUID> {
    Optional<FormTemplate> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
