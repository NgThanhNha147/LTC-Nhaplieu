package com.example.dynamicform.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchDocumentRepository extends JpaRepository<BatchDocument, UUID>, JpaSpecificationExecutor<BatchDocument> {
    long countByBatchId(UUID batchId);
    long countByBatchIdAndDeletedFalse(UUID batchId);
    boolean existsByBatchIdAndDocumentFileChecksumSha256(UUID batchId, String checksumSha256);
    boolean existsByDocumentFileIdAndDeletedFalseAndAssignedUserId(UUID documentFileId, UUID assignedUserId);
    List<BatchDocument> findByBatchIdAndDeletedFalseOrderBySequenceNoAsc(UUID batchId);
    Optional<BatchDocument> findFirstByBatchIdAndDeletedFalseAndSequenceNoLessThanOrderBySequenceNoDesc(UUID batchId, Integer sequenceNo);
    Optional<BatchDocument> findFirstByBatchIdAndDeletedFalseAndSequenceNoGreaterThanOrderBySequenceNoAsc(UUID batchId, Integer sequenceNo);
    Optional<BatchDocument> findFirstByBatchIdAndAssignedUserIdAndDeletedFalseAndSequenceNoLessThanOrderBySequenceNoDesc(
            UUID batchId, UUID assignedUserId, Integer sequenceNo);
    Optional<BatchDocument> findFirstByBatchIdAndAssignedUserIdAndDeletedFalseAndSequenceNoGreaterThanOrderBySequenceNoAsc(
            UUID batchId, UUID assignedUserId, Integer sequenceNo);

    @Query("select d.workflowStatus, count(d) from BatchDocument d where d.batch.id = :batchId and d.deleted = false group by d.workflowStatus")
    List<Object[]> countStatuses(@Param("batchId") UUID batchId);

    @Query("select count(d) from BatchDocument d where d.batch.id = :batchId and d.deleted = true")
    long countDeleted(@Param("batchId") UUID batchId);
}
