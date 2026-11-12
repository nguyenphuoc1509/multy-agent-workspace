package com.workspace.llm.published;

import java.util.List;

public record StreamRequest(String model, String system, List<LlmTurn> turns, int maxOutputTokens) {}
