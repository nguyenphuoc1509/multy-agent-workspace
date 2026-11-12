package com.workspace.run.domain;

public enum RunStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, BLOCKED, CANCELLED;

    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }
}
