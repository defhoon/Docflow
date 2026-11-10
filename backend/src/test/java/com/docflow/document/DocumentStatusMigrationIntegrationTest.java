package com.docflow.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * SPEC-07 AC-FIN-12: V7 상태 마이그레이션. 별도 스키마에 V6까지 적용하고 기존 상태 행을 넣은 뒤 V7을 적용한다.
 */
@SpringBootTest
@DirtiesContext
class DocumentStatusMigrationIntegrationTest {

	private static final String SCHEMA = "spec07_migration_test";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void dropSchema() {
		this.jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
	}

	@Test
	void collaborationStatusesBecomeActive() {
		migrate("6");
		seed("DRAFT", "IN_REVIEW", "CHANGES_REQUESTED", "RE_REVIEW");

		migrate(null);

		assertThat(this.jdbcTemplate.queryForList("SELECT DISTINCT status FROM " + SCHEMA + ".documents", String.class))
			.containsExactly("ACTIVE");
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM " + SCHEMA + ".documents WHERE final_version_id IS NULL AND finalized_by IS NULL AND finalized_at IS NULL",
				Long.class)).isEqualTo(4);
	}

	@Test
	void approvedDocumentsStopTheMigration() {
		migrate("6");
		seed("DRAFT", "APPROVED");

		assertThatThrownBy(() -> migrate(null)).isInstanceOf(FlywayException.class)
			.hasMessageContaining("APPROVED");
		assertThat(this.jdbcTemplate.queryForList("SELECT status FROM " + SCHEMA + ".documents ORDER BY id", String.class))
			.containsExactly("DRAFT", "APPROVED");
	}

	private void migrate(String target) {
		var config = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true)
			.locations("classpath:db/migration");
		if (target != null) {
			config.target(target);
		}
		config.load().migrate();
	}

	private void seed(String... statuses) {
		Long userId = this.jdbcTemplate.queryForObject("INSERT INTO " + SCHEMA
				+ ".users (email, password_hash, display_name) VALUES ('m@test.local', 'x', 'M') RETURNING id", Long.class);
		Long projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO " + SCHEMA + ".projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, userId);
		for (String status : statuses) {
			this.jdbcTemplate.update("INSERT INTO " + SCHEMA
					+ ".documents (project_id, title, status, created_by) VALUES (?, 't', ?, ?)", projectId, status, userId);
		}
	}

}
