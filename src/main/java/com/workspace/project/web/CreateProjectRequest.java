package com.workspace.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10_000) String description,
        @Positive Long dailyTokenBudget) {}
