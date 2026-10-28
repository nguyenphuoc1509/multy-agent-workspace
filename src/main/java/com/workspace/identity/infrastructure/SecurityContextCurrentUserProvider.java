package com.workspace.identity.infrastructure;

import com.workspace.identity.published.CurrentUser;
import com.workspace.identity.published.CurrentUserProvider;
import com.workspace.shared.published.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw ApiException.unauthenticated("Authentication required");
    }
}
