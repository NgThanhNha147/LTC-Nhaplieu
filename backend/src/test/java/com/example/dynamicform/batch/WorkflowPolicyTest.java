package com.example.dynamicform.batch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowPolicyTest {
    @Test
    void onlyEditableStatusesCanBeSavedAndSubmitted() {
        assertThat(WorkflowPolicy.canSaveDraft(WorkflowStatus.UNPROCESSED)).isTrue();
        assertThat(WorkflowPolicy.canSaveDraft(WorkflowStatus.DRAFT)).isTrue();
        assertThat(WorkflowPolicy.canSaveDraft(WorkflowStatus.REJECTED)).isTrue();
        assertThat(WorkflowPolicy.canSaveDraft(WorkflowStatus.PENDING_APPROVAL)).isFalse();
        assertThat(WorkflowPolicy.canSubmit(WorkflowStatus.APPROVED)).isFalse();
    }

    @Test
    void approvalActionsRequirePendingApproval() {
        assertThat(WorkflowPolicy.canApproveOrReject(WorkflowStatus.PENDING_APPROVAL)).isTrue();
        assertThat(WorkflowPolicy.canApproveOrReject(WorkflowStatus.DRAFT)).isFalse();
        assertThat(WorkflowPolicy.canReopen(WorkflowStatus.APPROVED)).isTrue();
        assertThat(WorkflowPolicy.canReopen(WorkflowStatus.PENDING_APPROVAL)).isFalse();
    }

    @Test
    void approvedRecordMustBeReopenedBeforeDeletion() {
        assertThat(WorkflowPolicy.canDelete(WorkflowStatus.APPROVED)).isFalse();
        assertThat(WorkflowPolicy.canDelete(WorkflowStatus.REJECTED)).isTrue();
    }
}
