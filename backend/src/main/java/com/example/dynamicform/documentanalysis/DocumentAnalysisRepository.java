package com.example.dynamicform.documentanalysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentAnalysisRepository extends JpaRepository<DocumentAnalysis, UUID> {
    Optional<DocumentAnalysis> findFirstByDocumentFileIdOrderByCreatedAtDesc(UUID documentId);
    List<DocumentAnalysis> findTop30ByOrderByUpdatedAtDesc();
}
