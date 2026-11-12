package com.workspace.agent.web;

import com.workspace.agent.application.RoleViews.ConfigVersionView;
import com.workspace.agent.published.ContextProfile;
import com.workspace.agent.published.Permission;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConfigVersionResponse(UUID id, UUID roleId, int versionNo, String systemPrompt,
                                    ContextProfile contextProfile, List<Permission> permissions, boolean isActive,
                                    Instant createdAt) {
    static ConfigVersionResponse from(ConfigVersionView v) {
        return new ConfigVersionResponse(v.id(), v.roleId(), v.versionNo(), v.systemPrompt(), v.contextProfile(),
                v.permissions(), v.active(), v.createdAt());
    }
}
