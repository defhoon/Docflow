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

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * AC-PROJECT-17: Flyway V3 스키마 제약. 컨텍스트 기동 자체가 {@code ddl-auto=validate} 통과를 의미한다.
 */
@SpringBootTest
class ProjectSchemaIntegrationTest {

	private static final String INSERT = "INSERT INTO projects (name, owner_id) VALUES (?, ?)";

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM projects");
		this.jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void checkConstraintRejectsAsciiSpaceOnlyName() {
		User owner = this.userAccountService.createUser("author@test.local", "Correct-Horse-Battery-9", "Author");

		assertThatThrownBy(() -> this.jdbcTemplate.update(INSERT, "   ", owner.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatCode(() -> this.jdbcTemplate.update(INSERT, "DocFlow", owner.getId())).doesNotThrowAnyException();
	}

	@Test
	void foreignKeyRejectsUnknownOwner() {
		assertThatThrownBy(() -> this.jdbcTemplate.update(INSERT, "DocFlow", Long.MAX_VALUE))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
