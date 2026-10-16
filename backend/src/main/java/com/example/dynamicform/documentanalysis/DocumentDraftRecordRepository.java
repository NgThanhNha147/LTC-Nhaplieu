package com.example.dynamicform.documentanalysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentDraftRecordRepository extends JpaRepository<DocumentDraftRecord, UUID> {
    Optional<DocumentDraftRecord> findByAnalysisId(UUID analysisId);
}
