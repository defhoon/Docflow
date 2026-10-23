package com.docflow.project.dto;

import java.time.Instant;

import com.docflow.project.domain.Project;

/**
 * 프로젝트 상세 응답. Owner는 표시에 필요한 정보만 포함한다 (이메일 미포함).
 */
public record ProjectResponse(Long id, String name, String description, Owner owner, Instant createdAt,
		Instant updatedAt) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
				new Owner(project.getOwner().getId(), project.getOwner().getDisplayName()), project.getCreatedAt(),
				project.getUpdatedAt());
	}

	public record Owner(Long id, String displayName) {
	}

}
