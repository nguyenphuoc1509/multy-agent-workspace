package com.workspace.llm.published;

@FunctionalInterface
public interface StreamListener {
    void onDelta(String text);
}
