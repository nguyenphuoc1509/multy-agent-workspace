package com.workspace.agent.application;

import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.Permission;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RoleViews {

    private RoleViews() {}

    public record ActiveConfig(UUID id, int versionNo) {}

    public record RoleView(UUID id, UUID projectId, String code, String name, boolean builtin,
                           ActiveConfig activeConfigVersion, Instant createdAt) {}

    public record ConfigVersionView(UUID id, UUID roleId, int versionNo, String systemPrompt,
                                    ContextProfile contextProfile, List<Permission> permissions, boolean active,
                                    Instant createdAt) {}

    /** Input for a new config version (already bound and bean-validated by the web layer). */
    public record ConfigInput(String systemPrompt, ContextProfile contextProfile, List<Permission> permissions) {}
}
