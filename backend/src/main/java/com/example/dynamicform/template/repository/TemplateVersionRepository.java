package com.example.dynamicform.template.repository;

import com.example.dynamicform.template.domain.TemplateVersion;
import com.example.dynamicform.template.domain.VersionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {
    List<TemplateVersion> findByTemplateIdOrderByVersionNoDesc(UUID templateId);
    Optional<TemplateVersion> findByTemplateIdAndVersionNo(UUID templateId, Integer versionNo);
    Optional<TemplateVersion> findFirstByTemplateCodeIgnoreCaseAndStatusOrderByVersionNoDesc(String code, VersionStatus status);
    Optional<TemplateVersion> findFirstByTemplateCodeIgnoreCaseOrderByVersionNoDesc(String code);
    Optional<TemplateVersion> findByTemplateCodeIgnoreCaseAndVersionNo(String code, Integer versionNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from TemplateVersion v where v.id = :id")
    Optional<TemplateVersion> findByIdForUpdate(@Param("id") UUID id);
}
