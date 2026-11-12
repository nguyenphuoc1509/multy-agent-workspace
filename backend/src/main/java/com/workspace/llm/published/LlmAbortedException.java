package com.workspace.llm.published;

/** Thrown by an adapter when shouldAbort returned true (cancel or time limit). */
public class LlmAbortedException extends RuntimeException {
    public LlmAbortedException() {
        super("aborted");
    }
}
