package com.workspace.agent.published;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Per-agent limits. null = use the server default when a run is created (M3). */
public record AgentParams(
        @Min(1) @Max(200_000) Integer maxOutputTokens,
        @Min(1) @Max(3_600) Integer maxRunSeconds,
        @Min(1_000) @Max(2_000_000) Integer maxContextTokens) {}
