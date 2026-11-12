package com.workspace.llm.published;

/** role is "user" or "assistant". */
public record LlmTurn(String role, String content) {}
