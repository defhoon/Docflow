package com.docflow.project.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 구성원 테이블 DB 제약 위반 판별 (SPEC-02 §7.3).
 * <p>
 * 409로 바꾸는 것은 {@code uk_project_members_project_user} 위반뿐이다. 예외 클래스만으로 판단하지 않고, 원인 체인에 있는
 * Hibernate {@link ConstraintViolationException}의 제약 이름으로 판단한다. 이름을 확인할 수 없거나 다른 제약이면
 * 중복으로 보지 않는다.
 */
final class ProjectMemberConstraints {

	static final String UNIQUE_PROJECT_USER = "uk_project_members_project_user";

	private ProjectMemberConstraints() {
	}

	static boolean isDuplicateMember(DataIntegrityViolationException ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return UNIQUE_PROJECT_USER.equalsIgnoreCase(violation.getConstraintName());
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return false;
	}

}
