package com.docflow.project;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.docflow.project.dto.CreateProjectRequest;
import com.docflow.project.service.ProjectService;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * AC-MEMBER-16: Flyway V4 스키마 제약. 컨텍스트 기동 자체가 {@code ddl-auto=validate} 통과를 의미한다.
 */
@SpringBootTest
class ProjectMemberSchemaIntegrationTest {

	private static final String INSERT = "INSERT INTO project_members (project_id, user_id) VALUES (?, ?)";

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User member;

	private long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		User owner = this.userAccountService.createUser("author@test.local", "Correct-Horse-Battery-9", "Author");
		this.member = this.userAccountService.createUser("reviewer@test.local", "Correct-Horse-Battery-9", "Reviewer");
		this.projectId = this.projectService.create(owner.getId(), new CreateProjectRequest("DocFlow", null)).id();
	}

	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM projects");
		this.jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void uniqueConstraintRejectsDuplicateMember() {
		assertThatCode(() -> this.jdbcTemplate.update(INSERT, this.projectId, this.member.getId()))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> this.jdbcTemplate.update(INSERT, this.projectId, this.member.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void foreignKeysRejectUnknownProjectOrUser() {
		assertThatThrownBy(() -> this.jdbcTemplate.update(INSERT, Long.MAX_VALUE, this.member.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbcTemplate.update(INSERT, this.projectId, Long.MAX_VALUE))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
