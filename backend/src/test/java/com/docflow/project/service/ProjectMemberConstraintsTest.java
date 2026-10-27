package com.docflow.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 409 변환 대상 판별 (SPEC-02 §7.3). 실제 PostgreSQL 오류의 판별은 {@code ProjectMemberConcurrencyIntegrationTest}에서
 * 검증한다.
 */
class ProjectMemberConstraintsTest {

	@Test
	void uniqueProjectUserViolationIsDuplicateRegardlessOfCase() {
		assertThat(ProjectMemberConstraints.isDuplicateMember(wrap("UK_PROJECT_MEMBERS_PROJECT_USER"))).isTrue();
		assertThat(ProjectMemberConstraints.isDuplicateMember(
				new DataIntegrityViolationException("outer", new RuntimeException(violation("uk_project_members_project_user")))))
			.isTrue();
	}

	@Test
	void otherConstraintsOrUnknownNamesAreNotDuplicate() {
		assertThat(ProjectMemberConstraints.isDuplicateMember(wrap("project_members_user_id_fkey"))).isFalse();
		assertThat(ProjectMemberConstraints.isDuplicateMember(wrap("uk_users_email"))).isFalse();
		assertThat(ProjectMemberConstraints.isDuplicateMember(wrap(null))).isFalse();
		assertThat(ProjectMemberConstraints.isDuplicateMember(new DataIntegrityViolationException("no cause")))
			.isFalse();
	}

	private static DataIntegrityViolationException wrap(String constraintName) {
		return new DataIntegrityViolationException("could not execute statement", violation(constraintName));
	}

	private static ConstraintViolationException violation(String constraintName) {
		return new ConstraintViolationException("violation", new SQLException("violation", "23505"), constraintName);
	}

}
