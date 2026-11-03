package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.document.storage.FileStorage;
import com.docflow.document.storage.StorageKeyGenerator;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-03 §10.2.1 저장 키 고정 통합 테스트와 저장·보상 실패 (AC-DOC-11, 12, 14, 28). 실제 PostgreSQL 제약 위반과
 * 실제 파일시스템을 사용한다. 저장 키 생성기만 테스트 구현으로 교체한다.
 * <p>
 * 테스트 트랜잭션이 결과를 가리지 않도록 {@code @Transactional}을 사용하지 않고 커밋된 DB 상태를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class DocumentStorageIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	/** 테스트별로 바꾸는 저장 키. 기본은 운영과 같은 UUID 형식이다. */
	static volatile Supplier<String> nextKey = DocumentStorageIntegrationTest::uuidKey;

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		DocumentTestSupport.registerStorage(registry);
	}

	@TestConfiguration
	static class FixedKeyConfig {

		@Bean
		@Primary
		StorageKeyGenerator testStorageKeyGenerator() {
			return () -> nextKey.get();
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private FileStorage fileStorage;

	private User owner;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, this.owner.getId());
	}

	@AfterEach
	void cleanUp() {
		nextKey = DocumentStorageIntegrationTest::uuidKey;
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-DOC-12 시나리오 A: 파일 배치 성공 → v1 INSERT가 실제 uk_document_versions_storage_key 위반 → 전체 롤백 → 보상 삭제
	@Test
	void scenarioA_uniqueViolationRollsBackDocumentAndVersionAndDeletesCreatedFile(CapturedOutput output)
			throws Exception {
		String key = "documents/fixed-" + UUID.randomUUID();
		Long existingDocument = insertExistingVersionRow(key);
		nextKey = () -> key;
		assertThat(DocumentTestSupport.STORAGE_ROOT.resolve(key)).doesNotExist();

		String body = upload().andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(output).contains("uk_document_versions_storage_key");
		// Document와 Version 모두 롤백 (사전 삽입 행만 남음)
		assertThat(this.jdbcTemplate.queryForList("SELECT id FROM documents", Long.class))
			.containsExactly(existingDocument);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions", Integer.class))
			.isEqualTo(1);
		// 이 요청이 CREATE_NEW로 만든 파일은 롤백 확정 후 삭제
		assertThat(DocumentTestSupport.storedFiles()).isEmpty();
		assertHidesInternals(body, key);
	}

	// AC-DOC-11, 28 시나리오 B: 같은 저장 키 파일이 이미 있음 → CREATE_NEW 실패 → 기존 파일 보존, DB 행 없음
	@Test
	void scenarioB_existingFileIsNeitherOverwrittenNorDeleted() throws Exception {
		String key = "documents/fixed-" + UUID.randomUUID();
		Path existing = DocumentTestSupport.STORAGE_ROOT.resolve(key);
		Files.createDirectories(existing.getParent());
		Files.writeString(existing, "existing content");
		nextKey = () -> key;

		String body = upload().andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(existing).hasContent("existing content");
		assertThat(DocumentTestSupport.storedFiles()).containsExactly(key);
		assertNoRows();
		assertHidesInternals(body, key);
	}

	// AC-DOC-11: 실제 파일시스템 저장 실패(최종 배치 단계) → DB 행 없음, 임시 파일 정리
	@Test
	void storageFailureLeavesNoRowsAndNoTempFiles() throws Exception {
		Files.writeString(DocumentTestSupport.STORAGE_ROOT.resolve("blocker"), "regular file");
		nextKey = () -> "blocker/child";

		upload().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		assertNoRows();
		assertThat(DocumentTestSupport.storedFiles()).containsExactly("blocker");
	}

	// AC-DOC-14: 보상 삭제 실패는 원래 오류(500)를 덮지 않고, 고아 파일은 남으며 저장 키가 로그에 남는다
	@Test
	void compensationFailureKeepsOriginalErrorAndLeavesOrphan(CapturedOutput output) throws Exception {
		String key = "documents/fixed-" + UUID.randomUUID();
		insertExistingVersionRow(key);
		nextKey = () -> key;
		willThrow(new IOException("simulated delete failure")).given(this.fileStorage).delete(anyString());

		upload().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		assertThat(DocumentTestSupport.storedFiles()).containsExactly(key);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class)).isEqualTo(1);
		assertThat(output).contains("orphan remains: storageKey=" + key);
	}

	// 성공 경로: 임시 파일은 남지 않고 최종 파일 하나만 남는다
	@Test
	void successLeavesOnlyFinalFile() throws Exception {
		String key = "documents/" + UUID.randomUUID();
		nextKey = () -> key;

		upload().andExpect(status().isCreated());

		assertThat(DocumentTestSupport.storedFiles()).containsExactly(key);
	}

	/** 같은 저장 키를 참조하는 기존 버전 행 (해당 파일은 디스크에 없음). */
	private Long insertExistingVersionRow(String key) {
		Long documentId = this.jdbcTemplate.queryForObject(
				"INSERT INTO documents (project_id, title, created_by) VALUES (?, 'existing', ?) RETURNING id",
				Long.class, this.projectId, this.owner.getId());
		this.jdbcTemplate.update("""
				INSERT INTO document_versions (document_id, version_number, original_filename, content_type, size_bytes,
				    storage_key, sha256, uploaded_by)
				VALUES (?, 1, 'e.pdf', 'application/pdf', 1, ?, ?, ?)""", documentId, key, "0".repeat(64),
				this.owner.getId());
		return documentId;
	}

	private ResultActions upload() throws Exception {
		return this.mockMvc.perform(multipart("/api/projects/" + this.projectId + "/documents")
			.file(new MockMultipartFile("file", "a.pdf", "application/pdf", DocumentTestFiles.pdf()))
			.param("title", "문서")
			.with(as(this.owner))
			.with(csrf()));
	}

	private void assertNoRows() {
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class)).isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions", Integer.class)).isZero();
	}

	private static void assertHidesInternals(String body, String key) {
		assertThat(body.toLowerCase()).doesNotContain(key)
			.doesNotContain("documents/")
			.doesNotContain("storage")
			.doesNotContain("sql")
			.doesNotContain("constraint")
			.doesNotContain("exception")
			.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString().toLowerCase());
	}

	private static String uuidKey() {
		return "documents/" + UUID.randomUUID();
	}

}
