package com.workspace.project.infrastructure;

import com.workspace.project.domain.ProjectMember;
import com.workspace.project.domain.ProjectMemberKey;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberKey> {
    List<ProjectMember> findByIdUserIdAndIdProjectIdIn(UUID userId, Collection<UUID> projectIds);
}
