package com.workspace.project.application;

import com.workspace.audit.published.AuditLog;
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
import java.util.LinkedHashMap;
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
    private final AuditLog audit;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectAccess access,
                          Clock clock, AuditLog audit) {
        this.projects = projects;
        this.members = members;
        this.access = access;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public ProjectView create(UUID userId, String name, String description, Long dailyTokenBudget) {
        // truncate to micros: PostgreSQL timestamptz precision, keeps keyset cursors exact
        Project p = projects.save(Project.create(name.trim(), description, dailyTokenBudget, userId,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        members.save(new ProjectMember(p.getId(), userId, ProjectRole.OWNER));
        audit.record(p.getId(), AuditLog.ACTOR_USER, userId.toString(), "PROJECT_CREATED", "PROJECT", p.getId(),
                null, snapshot(p));
        return view(p, ProjectRole.OWNER);
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
        Map<String, Object> before = snapshot(p);
        if (name != null) p.rename(name.trim());
        if (description != null) p.changeDescription(description);
        if (dailyTokenBudget != null) p.changeDailyTokenBudget(dailyTokenBudget);
        audit.record(projectId, AuditLog.ACTOR_USER, userId.toString(), "PROJECT_UPDATED", "PROJECT", projectId,
                before, snapshot(p));
        return view(p, m.role());
    }

    private static Map<String, Object> snapshot(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("dailyTokenBudget", p.getDailyTokenBudget());
        return m;
    }

    private static ProjectView view(Project p, ProjectRole role) {
        return new ProjectView(p.getId(), p.getName(), p.getDescription(), p.getDailyTokenBudget(), role, p.getCreatedAt());
    }
}
