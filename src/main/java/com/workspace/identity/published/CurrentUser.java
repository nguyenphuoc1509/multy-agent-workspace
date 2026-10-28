package com.workspace.identity.published;

import java.io.Serializable;
import java.util.UUID;

/** The authenticated user as seen by other modules. Stored as the Spring Security principal. */
public record CurrentUser(UUID id, String email, String displayName) implements Serializable {}
