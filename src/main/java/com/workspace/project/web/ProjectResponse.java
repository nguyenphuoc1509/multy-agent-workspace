package com.workspace.project.web;

import com.workspace.project.application.ProjectView;
import com.workspace.project.published.ProjectRole;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, String description, Long dailyTokenBudget,
                              ProjectRole myRole, Instant createdAt) {
    static ProjectResponse from(ProjectView v) {
        return new ProjectResponse(v.id(), v.name(), v.description(), v.dailyTokenBudget(), v.myRole(), v.createdAt());
    }
}
