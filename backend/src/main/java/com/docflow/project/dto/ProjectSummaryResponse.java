package com.docflow.project.dto;

import java.time.Instant;

import com.docflow.project.domain.Project;
import com.docflow.project.domain.ProjectRole;

/**
 * 프로젝트 목록 항목. Owner를 포함하지 않아 목록 조회에서 사용자 지연 로딩이 발생하지 않는다.
 */
public record ProjectSummaryResponse(Long id, String name, Instant createdAt, ProjectRole myRole) {

	public static ProjectSummaryResponse from(Project project, ProjectRole myRole) {
		return new ProjectSummaryResponse(project.getId(), project.getName(), project.getCreatedAt(), myRole);
	}

}
