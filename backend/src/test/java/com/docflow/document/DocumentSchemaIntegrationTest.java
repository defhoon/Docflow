package com.docflow.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * AC-DOC-22: Flyway V5 스키마의 FK·UNIQUE·CHECK를 직접 INSERT로 확인한다 ({@code ddl-auto=validate}는 컨텍스트 기동으로
 * 확인).
 */
@SpringBootTest
class DocumentSchemaIntegrationTest {

	private static final String SHA = "a".repeat(64);

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		DocumentTestSupport.registerStorage(registry);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long userId;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.userId = this.jdbcTemplate.queryForObject(
				"INSERT INTO users (email, password_hash, display_name) VALUES ('s@test.local', 'h', 'S') RETURNING id",
				Long.class);
		this.projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, this.userId);
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
	}

	@Test
	void documentDefaultsAndChecks() {
		Long id = insertDocument("제목");
		assertThat(this.jdbcTemplate.queryForObject("SELECT status FROM documents WHERE id = ?", String.class, id))
			.isEqualTo("DRAFT");

		assertViolation(() -> insertDocument("   "), "ck_documents_title_not_blank");
		assertViolation(() -> this.jdbcTemplate.update(
				"INSERT INTO documents (project_id, title, status, created_by) VALUES (?, 't', 'DELETED', ?)",
				this.projectId, this.userId), "ck_documents_status");
		assertViolation(() -> this.jdbcTemplate.update(
				"INSERT INTO documents (project_id, title, created_by) VALUES (?, 't', ?)", Long.MAX_VALUE,
				this.userId), "documents_project_id_fkey");
	}

	@Test
	void versionUniqueAndCheckConstraints() {
		Long documentId = insertDocument("t");
		insertVersion(documentId, 1, "k1", 10, SHA, "a.pdf");

		assertViolation(() -> insertVersion(documentId, 1, "k2", 10, SHA, "a.pdf"),
				"uk_document_versions_document_version");
		assertViolation(() -> insertVersion(documentId, 2, "k1", 10, SHA, "a.pdf"),
				"uk_document_versions_storage_key");
		assertViolation(() -> insertVersion(documentId, 0, "k3", 10, SHA, "a.pdf"),
				"ck_document_versions_version_positive");
		assertViolation(() -> insertVersion(documentId, 3, "k4", 0, SHA, "a.pdf"),
				"ck_document_versions_size_positive");
		assertViolation(() -> insertVersion(documentId, 4, "k5", 10, "A".repeat(64), "a.pdf"),
				"ck_document_versions_sha256");
		assertViolation(() -> insertVersion(documentId, 5, "k6", 10, SHA, "  "),
				"ck_document_versions_filename_not_blank");
	}

	@Test
	void foreignKeysRestrictDeletion() {
		Long documentId = insertDocument("t");
		insertVersion(documentId, 1, "k1", 10, SHA, "a.pdf");

		assertThatThrownBy(() -> this.jdbcTemplate.update("DELETE FROM documents WHERE id = ?", documentId))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbcTemplate.update("DELETE FROM projects WHERE id = ?", this.projectId))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbcTemplate.update("DELETE FROM users WHERE id = ?", this.userId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Long insertDocument(String title) {
		return this.jdbcTemplate.queryForObject(
				"INSERT INTO documents (project_id, title, created_by) VALUES (?, ?, ?) RETURNING id", Long.class,
				this.projectId, title, this.userId);
	}

	private void insertVersion(Long documentId, int version, String key, long size, String sha, String filename) {
		this.jdbcTemplate.update("""
				INSERT INTO document_versions (document_id, version_number, original_filename, content_type, size_bytes,
				    storage_key, sha256, uploaded_by)
				VALUES (?, ?, ?, 'application/pdf', ?, ?, ?, ?)""", documentId, version, filename, size, key, sha,
				this.userId);
	}

	private static void assertViolation(Runnable statement, String constraint) {
		assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(constraint);
	}

}
