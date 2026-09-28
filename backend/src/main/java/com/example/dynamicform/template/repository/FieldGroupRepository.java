package com.example.dynamicform.template.repository;

import com.example.dynamicform.template.domain.FieldGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FieldGroupRepository extends JpaRepository<FieldGroup, UUID> {
    List<FieldGroup> findByTemplateVersionIdOrderByDisplayOrderAsc(UUID versionId);
    boolean existsByTemplateVersionIdAndCodeIgnoreCase(UUID versionId, String code);
    boolean existsByTemplateVersionIdAndCodeIgnoreCaseAndIdNot(UUID versionId, String code, UUID id);
}
