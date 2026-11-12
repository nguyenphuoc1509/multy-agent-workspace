package com.workspace.project.web;

import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.project.application.ProjectService;
import com.workspace.shared.published.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService projects;
    private final CurrentUserProvider currentUser;

    public ProjectController(ProjectService projects, CurrentUserProvider currentUser) {
        this.projects = projects;
        this.currentUser = currentUser;
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest body) {
        UUID userId = currentUser.require().id();
        return ProjectResponse.from(projects.create(userId, body.name(), body.description(), body.dailyTokenBudget()));
    }

    @GetMapping("/projects")
    public PageResponse<ProjectResponse> list(@RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        UUID userId = currentUser.require().id();
        PageResponse<com.workspace.project.application.ProjectView> page = projects.list(userId, cursor, limit);
        return new PageResponse<>(page.items().stream().map(ProjectResponse::from).toList(), page.nextCursor());
    }

    @GetMapping("/projects/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return ProjectResponse.from(projects.get(projectId, currentUser.require().id()));
    }

    @PatchMapping("/projects/{projectId}")
    public ProjectResponse update(@PathVariable UUID projectId, @Valid @RequestBody UpdateProjectRequest body) {
        UUID userId = currentUser.require().id();
        return ProjectResponse.from(projects.update(projectId, userId, body.name(), body.description(), body.dailyTokenBudget()));
    }
}
