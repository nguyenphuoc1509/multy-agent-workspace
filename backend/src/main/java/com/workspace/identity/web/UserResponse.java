package com.workspace.identity.web;

import com.workspace.identity.published.CurrentUser;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName) {
    static UserResponse from(CurrentUser u) {
        return new UserResponse(u.id(), u.email(), u.displayName());
    }
}
