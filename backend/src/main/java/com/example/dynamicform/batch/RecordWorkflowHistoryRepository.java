package com.example.dynamicform.batch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecordWorkflowHistoryRepository extends JpaRepository<RecordWorkflowHistory, UUID> {
    List<RecordWorkflowHistory> findByBatchDocumentIdOrderByPerformedAtDesc(UUID batchDocumentId);
}
