package io.seqera.tower.enums

enum WorkflowAction {

    WORKFLOW_UPDATE, PROGRESS_UPDATE;

    // weird hack, to get the action rendered properly
    // when in the log output, w/o it's reported as `WorkflowAction()`
    String toString() { super.toString() }

}