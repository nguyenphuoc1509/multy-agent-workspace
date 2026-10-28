package com.workspace.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** Column exists but is unused in the MVP (no Git integration). */
    @Column(name = "git_repo_url")
    private String gitRepoUrl;

    @Column(name = "daily_token_budget")
    private Long dailyTokenBudget;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Project() {}

    public static Project create(String name, String description, Long dailyTokenBudget, UUID createdBy, Instant now) {
        Project p = new Project();
        p.name = name;
        p.description = description;
        p.dailyTokenBudget = dailyTokenBudget;
        p.createdBy = createdBy;
        p.createdAt = now;
        return p;
    }

    public void rename(String name) { this.name = name; }
    public void changeDescription(String description) { this.description = description; }
    public void changeDailyTokenBudget(Long budget) { this.dailyTokenBudget = budget; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getDailyTokenBudget() { return dailyTokenBudget; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
