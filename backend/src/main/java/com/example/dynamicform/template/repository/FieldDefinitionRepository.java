package com.example.dynamicform.template.repository;

import com.example.dynamicform.template.domain.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {
    List<FieldDefinition> findByTemplateVersionIdOrderByDisplayOrderAsc(UUID versionId);
    Optional<FieldDefinition> findByTemplateVersionIdAndFieldCode(UUID versionId, String fieldCode);
    boolean existsByTemplateVersionIdAndFieldCodeIgnoreCase(UUID versionId, String fieldCode);
    boolean existsByTemplateVersionIdAndColumnNameIgnoreCase(UUID versionId, String columnName);
    boolean existsByTemplateVersionIdAndFieldCodeIgnoreCaseAndIdNot(UUID versionId, String fieldCode, UUID id);
    boolean existsByTemplateVersionIdAndColumnNameIgnoreCaseAndIdNot(UUID versionId, String columnName, UUID id);

    @Modifying
    @Query("update FieldDefinition field set field.businessKey = false "
            + "where field.templateVersion.id = :versionId and field.id <> :selectedId and field.businessKey = true")
    int clearBusinessKeyExcept(@Param("versionId") UUID versionId, @Param("selectedId") UUID selectedId);
}
