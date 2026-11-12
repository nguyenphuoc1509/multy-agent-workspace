package com.workspace.project.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** All fields optional; a field that is absent (or null) is left unchanged. */
public record UpdateProjectRequest(
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 10_000) String description,
        @Positive Long dailyTokenBudget) {}
