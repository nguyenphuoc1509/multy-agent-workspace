package com.workspace.project.application;

import com.workspace.project.domain.Project;
import com.workspace.project.domain.ProjectMember;
import com.workspace.project.infrastructure.ProjectMemberRepository;
import com.workspace.project.infrastructure.ProjectRepository;
import com.workspace.project.published.ProjectAccess;
import com.workspace.project.published.ProjectMembership;
import com.workspace.project.published.ProjectRole;
import com.workspace.shared.published.ApiException;
import com.workspace.shared.published.PageResponse;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectAccess access;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectAccess access, Clock clock) {
        this.projects = projects;
        this.members = members;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public ProjectView create(UUID userId, String name, String description, Long dailyTokenBudget) {
        // truncate to micros: PostgreSQL timestamptz precision, keeps keyset cursors exact
        Project p = projects.save(Project.create(name.trim(), description, dailyTokenBudget, userId,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        members.save(new ProjectMember(p.getId(), userId, ProjectRole.OWNER));
        return view(p, ProjectRole.OWNER);
        // TODO(M4/M1): audit PROJECT_CREATED once audit_log exists (V9) -- see M0 report, open question 1
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectView> list(UUID userId, String cursor, Integer limitParam) {
        int limit = limitParam == null ? DEFAULT_LIMIT : limitParam;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiException.validation("limit must be between 1 and " + MAX_LIMIT);
        }
        int fetch = limit + 1;
        List<Project> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = projects.firstPage(userId, fetch);
        } else {
            ProjectCursor c = ProjectCursor.decode(cursor);
            rows = projects.pageAfter(userId, c.createdAt(), c.id(), fetch);
        }
        boolean hasMore = rows.size() > limit;
        List<Project> page = hasMore ? rows.subList(0, limit) : rows;

        Map<UUID, ProjectRole> roles = members
                .findByIdUserIdAndIdProjectIdIn(userId, page.stream().map(Project::getId).toList())
                .stream().collect(Collectors.toMap(m -> m.getId().getProjectId(), ProjectMember::getRole, (a, b) -> a));

        List<ProjectView> items = page.stream().map(p -> view(p, roles.get(p.getId()))).toList();
        String next = hasMore
                ? new ProjectCursor(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getId()).encode()
                : null;
        return new PageResponse<>(items, next);
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID projectId, UUID userId) {
        ProjectMembership m = access.requireMember(projectId, userId);
        Project p = projects.findById(projectId).orElseThrow(ApiException::notFound);
        return view(p, m.role());
    }

    /** Absent field = unchanged. (Clearing the budget is an open question, see M0 report.) */
    @Transactional
    public ProjectView update(UUID projectId, UUID userId, String name, String description, Long dailyTokenBudget) {
        ProjectMembership m = access.requireRole(projectId, userId, Set.of(ProjectRole.OWNER));
        Project p = projects.findById(projectId).orElseThrow(ApiException::notFound);
        if (name != null) p.rename(name.trim());
        if (description != null) p.changeDescription(description);
        if (dailyTokenBudget != null) p.changeDailyTokenBudget(dailyTokenBudget);
        return view(p, m.role());
    }

    private static ProjectView view(Project p, ProjectRole role) {
        return new ProjectView(p.getId(), p.getName(), p.getDescription(), p.getDailyTokenBudget(), role, p.getCreatedAt());
    }
}
