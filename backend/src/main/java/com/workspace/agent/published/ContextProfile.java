package com.workspace.agent.published;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** What a role may see (brief section 7). includeUnapproved must be false in the MVP. */
public record ContextProfile(
        @NotNull @Valid List<@NotNull @Valid ContextDocument> documents,
        boolean includeUnapproved,
        @NotNull @Min(1) @Max(100) Integer recentMessages) {}
