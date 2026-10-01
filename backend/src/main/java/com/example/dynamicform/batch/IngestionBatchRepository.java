package com.example.dynamicform.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, UUID>, JpaSpecificationExecutor<IngestionBatch> {
    boolean existsByBatchCode(String batchCode);
}
