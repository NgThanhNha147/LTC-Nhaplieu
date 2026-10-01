package com.example.dynamicform.batch;

import java.util.Set;

final class WorkflowPolicy {
    private static final Set<WorkflowStatus> EDITABLE = Set.of(
            WorkflowStatus.UNPROCESSED, WorkflowStatus.DRAFT, WorkflowStatus.REJECTED);

    private WorkflowPolicy() {}

    static boolean canSaveDraft(WorkflowStatus status) { return EDITABLE.contains(status); }
    static boolean canSubmit(WorkflowStatus status) { return EDITABLE.contains(status); }
    static boolean canApproveOrReject(WorkflowStatus status) { return status == WorkflowStatus.PENDING_APPROVAL; }
    static boolean canReopen(WorkflowStatus status) { return status == WorkflowStatus.APPROVED; }
    static boolean canDelete(WorkflowStatus status) { return status != WorkflowStatus.APPROVED; }
}
