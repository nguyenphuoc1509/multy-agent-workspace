package com.workspace.agent.web;

import com.workspace.agent.application.RoleViews.ActiveConfig;
import com.workspace.agent.application.RoleViews.RoleView;
import java.util.UUID;

public record RoleResponse(UUID id, String code, String name, boolean isBuiltin, ActiveConfig activeConfigVersion) {
    static RoleResponse from(RoleView v) {
        return new RoleResponse(v.id(), v.code(), v.name(), v.builtin(), v.activeConfigVersion());
    }
}
