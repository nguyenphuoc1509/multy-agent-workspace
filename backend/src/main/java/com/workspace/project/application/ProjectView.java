package com.workspace.project.application;

import com.workspace.project.published.ProjectRole;
import java.time.Instant;
import java.util.UUID;

public record ProjectView(UUID id, String name, String description, Long dailyTokenBudget,
                          ProjectRole myRole, Instant createdAt) {}
