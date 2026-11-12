package com.workspace.agent.application;

import com.workspace.project.published.ProjectCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Every new project gets the five default roles, in the same transaction that creates the project. */
@Component
class DefaultRolesSeeder {

    private final RoleService roles;

    DefaultRolesSeeder(RoleService roles) {
        this.roles = roles;
    }

    @EventListener
    void on(ProjectCreated event) {
        roles.seedDefaults(event.projectId(), event.createdBy());
    }
}
