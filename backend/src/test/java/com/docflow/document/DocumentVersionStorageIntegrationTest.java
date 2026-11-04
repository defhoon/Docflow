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
import java.util.List;
import java.util.Map;
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
import org.springframework.test.annotation.DirtiesContext;
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
 * SPEC-04 §7.3 새 버전의 저장·DB 실패와 보상 (AC-VER-08·09·11, [기존] SPEC-03 §10.2.1 A·B 방식). 실제 PostgreSQL 제약
 * 위반과 실제 파일시스템을 사용하고, 저장 키 생성기만 테스트 구현으로 교체한다. 기존 버전(v1)의 행·파일과 문서의
 * {@code updated_at}이 어떤 실패에서도 바뀌지 않음을 확인한다.
 * <p>
 * 기존 테스트 컨텍스트가 캐시에 남아 있는 상태에서 PostgreSQL 기본 연결 상한(100)을 넘지 않도록, 클래스가 끝나면 이
 * 컨텍스트(Hikari 풀)를 닫는다.
 */
@DirtiesContext
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class DocumentVersionStorageIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	/** 테스트별로 바꾸는 저장 키. 기본은 운영과 같은 UUID 형식이다. */
	static volatile Supplier<String> nextKey = DocumentVersionStorageIntegrationTest::uuidKey;

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

	private Long documentId;

	private String v1Key;

	private Map<String, Object> documentBefore;

	private Map<String, Object> v1Before;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, this.owner.getId());
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents").file(pdf())
				.param("title", "문서")
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		this.documentId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		this.v1Key = this.jdbcTemplate.queryForObject("SELECT storage_key FROM document_versions WHERE document_id = ?",
				String.class, this.documentId);
		this.documentBefore = documentRow();
		this.v1Before = v1Row();
	}

	@AfterEach
	void cleanUp() {
		nextKey = DocumentVersionStorageIntegrationTest::uuidKey;
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-VER-09 시나리오 A: 새 파일 배치 성공 → INSERT가 실제 uk_document_versions_storage_key 위반 → 롤백 → 이 요청 파일만 삭제
	@Test
	void scenarioA_databaseFailureRollsBackAndDeletesOnlyThisRequestsFile(CapturedOutput output) throws Exception {
		String key = "documents/fixed-" + UUID.randomUUID();
		insertForeignVersionRow(key);
		nextKey = () -> key;
		assertThat(DocumentTestSupport.STORAGE_ROOT.resolve(key)).doesNotExist();

		String body = uploadVersion().andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(output).contains("uk_document_versions_storage_key").doesNotContain("Unexpected version number conflict");
		assertThat(versionNumbers()).containsExactly(1);
		assertThat(documentRow()).isEqualTo(this.documentBefore);
		assertThat(v1Row()).isEqualTo(this.v1Before);
		assertThat(DocumentTestSupport.storedFiles()).containsExactly(this.v1Key);
		assertHidesInternals(body, key);
	}

	// AC-VER-08 시나리오 B: 저장 키가 기존 버전 파일과 충돌 → CREATE_NEW 실패 → 기존 파일 보존, 새 행 없음
	@Test
	void scenarioB_existingVersionFileIsNeitherOverwrittenNorDeleted() throws Exception {
		Path v1File = DocumentTestSupport.STORAGE_ROOT.resolve(this.v1Key);
		byte[] v1Content = Files.readAllBytes(v1File);
		nextKey = () -> this.v1Key;

		String body = uploadVersion().andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(Files.readAllBytes(v1File)).isEqualTo(v1Content);
		assertThat(DocumentTestSupport.storedFiles()).containsExactly(this.v1Key);
		assertThat(versionNumbers()).containsExactly(1);
		assertThat(documentRow()).isEqualTo(this.documentBefore);
		assertHidesInternals(body, this.v1Key);
	}

	// AC-VER-08: 실제 파일시스템 저장 실패(최종 배치) → 새 행 없음, 임시 파일 정리
	@Test
	void storageFailureLeavesNoRowsAndNoTempFiles() throws Exception {
		Files.writeString(DocumentTestSupport.STORAGE_ROOT.resolve("blocker"), "regular file");
		nextKey = () -> "blocker/child";

		uploadVersion().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		assertThat(versionNumbers()).containsExactly(1);
		assertThat(documentRow()).isEqualTo(this.documentBefore);
		assertThat(DocumentTestSupport.storedFiles()).containsExactlyInAnyOrder(this.v1Key, "blocker");
	}

	// AC-VER-08: 저장소 I/O 오류(테스트 대역) → 500, 새 행 없음, 응답에 내부 정보 없음
	@Test
	void storageIoErrorLeavesNoRows() throws Exception {
		willThrow(new IOException("simulated disk failure at " + DocumentTestSupport.STORAGE_ROOT)).given(this.fileStorage)
			.store(org.mockito.ArgumentMatchers.any(Path.class));

		String body = uploadVersion().andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(versionNumbers()).containsExactly(1);
		assertThat(DocumentTestSupport.storedFiles()).containsExactly(this.v1Key);
		assertHidesInternals(body, this.v1Key);
	}

	// AC-VER-11: 보상 삭제 실패는 원래 오류(500)를 덮지 않고, 고아 파일이 남으며 저장 키가 로그에 남는다
	@Test
	void compensationFailureKeepsOriginalErrorAndLeavesOrphan(CapturedOutput output) throws Exception {
		String key = "documents/fixed-" + UUID.randomUUID();
		insertForeignVersionRow(key);
		nextKey = () -> key;
		willThrow(new IOException("simulated delete failure")).given(this.fileStorage).delete(anyString());

		uploadVersion().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		assertThat(DocumentTestSupport.storedFiles()).containsExactlyInAnyOrder(this.v1Key, key);
		assertThat(versionNumbers()).containsExactly(1);
		assertThat(output).contains("orphan remains: storageKey=" + key);
	}

	/** 다른 문서의 버전 행이 저장 키를 이미 참조한다 (해당 파일은 디스크에 없음). */
	private void insertForeignVersionRow(String key) {
		Long other = this.jdbcTemplate.queryForObject(
				"INSERT INTO documents (project_id, title, created_by) VALUES (?, 'other', ?) RETURNING id", Long.class,
				this.projectId, this.owner.getId());
		this.jdbcTemplate.update("""
				INSERT INTO document_versions (document_id, version_number, original_filename, content_type, size_bytes,
				    storage_key, sha256, uploaded_by)
				VALUES (?, 1, 'e.pdf', 'application/pdf', 1, ?, ?, ?)""", other, key, "0".repeat(64),
				this.owner.getId());
	}

	private ResultActions uploadVersion() throws Exception {
		return this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents/" + this.documentId + "/versions")
				.file(pdf())
				.with(as(this.owner))
				.with(csrf()));
	}

	private Map<String, Object> documentRow() {
		return this.jdbcTemplate.queryForMap("SELECT * FROM documents WHERE id = ?", this.documentId);
	}

	private Map<String, Object> v1Row() {
		return this.jdbcTemplate.queryForMap(
				"SELECT * FROM document_versions WHERE document_id = ? AND version_number = 1", this.documentId);
	}

	private List<Integer> versionNumbers() {
		return this.jdbcTemplate.queryForList(
				"SELECT version_number FROM document_versions WHERE document_id = ? ORDER BY version_number",
				Integer.class, this.documentId);
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

	private static MockMultipartFile pdf() {
		return new MockMultipartFile("file", "a.pdf", "application/pdf", DocumentTestFiles.pdf());
	}

	private static String uuidKey() {
		return "documents/" + UUID.randomUUID();
	}

}
