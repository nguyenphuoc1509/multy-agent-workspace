package com.workspace.project.published;

import java.util.Optional;
import java.util.UUID;

public interface ProjectBudgets {
    /** Empty when the project has no daily token budget (unlimited). */
    Optional<Long> dailyTokenBudget(UUID projectId);
}
