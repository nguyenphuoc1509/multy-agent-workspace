package com.workspace.identity.published;

/** Port other modules use to learn who is calling. Never reads identity from a request body. */
public interface CurrentUserProvider {
    /** @throws com.workspace.shared.published.ApiException 401 UNAUTHENTICATED when nobody is logged in */
    CurrentUser require();
}
