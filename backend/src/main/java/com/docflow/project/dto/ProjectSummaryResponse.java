package com.docflow.project.dto;

import java.time.Instant;

import com.docflow.project.domain.Project;

/**
 * 프로젝트 목록 항목. Owner를 포함하지 않아 목록 조회에서 사용자 지연 로딩이 발생하지 않는다.
 */
public record ProjectSummaryResponse(Long id, String name, Instant createdAt) {

	public static ProjectSummaryResponse from(Project project) {
		return new ProjectSummaryResponse(project.getId(), project.getName(), project.getCreatedAt());
	}

}
