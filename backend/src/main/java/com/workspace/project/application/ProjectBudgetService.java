package com.workspace.project.application;

import com.workspace.project.domain.Project;
import com.workspace.project.infrastructure.ProjectRepository;
import com.workspace.project.published.ProjectBudgets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ProjectBudgetService implements ProjectBudgets {

    private final ProjectRepository projects;

    ProjectBudgetService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> dailyTokenBudget(UUID projectId) {
        return projects.findById(projectId).map(Project::getDailyTokenBudget);
    }
}
