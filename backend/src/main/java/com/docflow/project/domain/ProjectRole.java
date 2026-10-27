package com.docflow.project.domain;

/**
 * 프로젝트 안에서의 역할 (SPEC-02 §5.1). OWNER는 {@code projects.owner_id}, MEMBER는 {@code project_members} 행으로
 * 판단한다.
 */
public enum ProjectRole {

	OWNER, MEMBER

}
