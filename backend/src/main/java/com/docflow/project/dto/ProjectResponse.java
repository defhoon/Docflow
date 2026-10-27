package com.docflow.project.dto;

import java.time.Instant;

import com.docflow.project.domain.Project;
import com.docflow.project.domain.ProjectRole;

/**
 * 프로젝트 상세 응답. Owner는 표시에 필요한 정보만 포함한다 (이메일 미포함). {@code myRole}은 요청한 사용자의 역할이다.
 */
public record ProjectResponse(Long id, String name, String description, Owner owner, Instant createdAt,
		Instant updatedAt, ProjectRole myRole) {

	public static ProjectResponse from(Project project, ProjectRole myRole) {
		return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
				new Owner(project.getOwner().getId(), project.getOwner().getDisplayName()), project.getCreatedAt(),
				project.getUpdatedAt(), myRole);
	}

	public record Owner(Long id, String displayName) {
	}

}
