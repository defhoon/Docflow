package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.docflow.document.controller.DocumentController;
import com.docflow.document.controller.DocumentVersionController;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SPEC-04 새 버전 등록 API 통합 테스트 (실제 PostgreSQL, 보안 설정 활성 상태, MockMvc multipart). 문서 상태는 상태 전이
 * 기능이 없으므로 JDBC로 바꾼다 (§11.2).
 * <p>
 * 기존 테스트 컨텍스트가 캐시에 남아 있는 상태에서 PostgreSQL 기본 연결 상한(100)을 넘지 않도록, 클래스가 끝나면 이
 * 컨텍스트(Hikari 풀)를 닫는다.
 */
@DirtiesContext
@SpringBootTest
@AutoConfigureMockMvc
class DocumentVersionApiIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		DocumentTestSupport.registerStorage(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private User owner;

	private User member;

	private User outsider;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.member = this.userAccountService.createUser("member@test.local", PASSWORD, "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", PASSWORD, "Outsider");
		this.projectId = createProject(this.owner, "P1");
		addMember(this.projectId, this.member);
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-VER-01, 02, 15: 작성자가 아닌 MEMBER의 v2 등록. 응답 계약은 SPEC-03과 같고, 기존 버전·문서 정보는 불변, updated_at만 갱신
	@Test
	void memberWhoIsNotAuthorUploadsVersionTwoWithoutChangingExistingData() throws Exception {
		byte[] v1Content = DocumentTestFiles.pdf();
		Long documentId = createDocument(this.owner, "요구사항", v1Content);
		this.jdbcTemplate.update("UPDATE documents SET updated_at = updated_at - interval '1 hour' WHERE id = ?",
				documentId);
		Map<String, Object> documentBefore = documentRow(documentId);
		Map<String, Object> v1Before = versionRow(documentId, 1);
		byte[] v2Content = DocumentTestFiles.valid(DocumentFileType.DOCX);

		String body = uploadVersion(this.member, documentId, file("요구사항-v2.docx", v2Content))
			.andExpect(status().isCreated())
			.andExpect(handler().handlerType(DocumentVersionController.class))
			.andExpect(header().string("Location", "/api/projects/" + this.projectId + "/documents/" + documentId))
			.andExpect(jsonPath("$.id").value(documentId))
			.andExpect(jsonPath("$.title").value("요구사항"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.createdBy.id").value(this.owner.getId()))
			.andExpect(jsonPath("$.createdBy.*", hasSize(2)))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2))
			.andExpect(jsonPath("$.latestVersion.originalFilename").value("요구사항-v2.docx"))
			.andExpect(jsonPath("$.latestVersion.sizeBytes").value(v2Content.length))
			.andExpect(jsonPath("$.latestVersion.uploadedAt").exists())
			.andExpect(jsonPath("$.latestVersion.*", hasSize(4)))
			.andExpect(jsonPath("$.*", hasSize(8)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		Map<String, Object> v2 = versionRow(documentId, 2);
		assertThat(v2.get("uploaded_by")).isEqualTo(this.member.getId());
		assertThat(v2.get("content_type")).isEqualTo(DocumentFileType.DOCX.contentType());
		assertThat(v2.get("size_bytes")).isEqualTo((long) v2Content.length);
		assertThat(v2.get("sha256")).isEqualTo(sha256(v2Content));
		String v2Key = (String) v2.get("storage_key");
		assertThat(Files.readAllBytes(DocumentTestSupport.STORAGE_ROOT.resolve(v2Key))).isEqualTo(v2Content);

		// 기존 버전 행 전 컬럼과 파일 내용 불변
		assertThat(versionRow(documentId, 1)).isEqualTo(v1Before);
		String v1Key = (String) v1Before.get("storage_key");
		assertThat(sha256(Files.readAllBytes(DocumentTestSupport.STORAGE_ROOT.resolve(v1Key))))
			.isEqualTo(v1Before.get("sha256"))
			.isEqualTo(sha256(v1Content));
		// 문서는 updated_at만 바뀐다
		Map<String, Object> documentAfter = documentRow(documentId);
		for (String column : List.of("project_id", "title", "description", "status", "created_by", "created_at")) {
			assertThat(documentAfter.get(column)).as(column).isEqualTo(documentBefore.get(column));
		}
		Instant updatedBefore = ((Timestamp) documentBefore.get("updated_at")).toInstant();
		Instant updatedAfter = ((Timestamp) documentAfter.get("updated_at")).toInstant();
		assertThat(updatedAfter).isAfter(updatedBefore);
		// 응답 updatedAt은 DB에 기록된 값과 같다 (PostgreSQL 마이크로초 정밀도)
		Instant responseUpdatedAt = Instant.parse(this.jsonMapper.readTree(body).get("updatedAt").asString());
		assertThat(Duration.between(responseUpdatedAt, updatedAfter).abs()).isLessThanOrEqualTo(Duration.ofNanos(1000));

		// AC-VER-15: 내부 정보 미노출
		assertThat(body).doesNotContain(v2Key)
			.doesNotContain(v1Key)
			.doesNotContain("sha256")
			.doesNotContain(sha256(v2Content))
			.doesNotContain("contentType")
			.doesNotContain("storage")
			.doesNotContain("uploadedBy")
			.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString())
			.doesNotContain("@test.local");
		assertThat(DocumentTestSupport.storedFiles()).containsExactlyInAnyOrder(v1Key, v2Key);
	}

	// AC-VER-01, 03, 15: OWNER 연속 등록 v2·v3, 상세·목록 반영, 목록 순서 불변, 기존 Controller 동작 유지
	@Test
	void consecutiveUploadsAreReflectedInDetailAndListWithoutReordering() throws Exception {
		Long older = createDocument(this.owner, "먼저 등록", DocumentTestFiles.pdf());
		Long newer = createDocument(this.owner, "나중 등록", DocumentTestFiles.pdf());

		uploadVersion(this.owner, older, pdfFile()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2));
		uploadVersion(this.owner, older, file("v3.xlsx", DocumentTestFiles.valid(DocumentFileType.XLSX)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(3))
			.andExpect(jsonPath("$.latestVersion.originalFilename").value("v3.xlsx"));

		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + older).with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(handler().handlerType(DocumentController.class))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(3))
			.andExpect(jsonPath("$.latestVersion.originalFilename").value("v3.xlsx"));
		this.mockMvc.perform(get(documentsUrl(this.projectId)).with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(handler().handlerType(DocumentController.class))
			.andExpect(jsonPath("$[0].id").value(newer))
			.andExpect(jsonPath("$[0].latestVersionNumber").value(1))
			.andExpect(jsonPath("$[1].id").value(older))
			.andExpect(jsonPath("$[1].latestVersionNumber").value(3));
		assertThat(versionNumbers(older)).containsExactly(1, 2, 3);
		assertThat(versionNumbers(newer)).containsExactly(1);
	}

	// AC-VER-05: CHANGES_REQUESTED는 허용되고 상태가 유지된다
	@Test
	void changesRequestedAcceptsNewVersionAndKeepsStatus() throws Exception {
		Long documentId = createDocument(this.owner, "수정 요청됨", DocumentTestFiles.pdf());
		setStatus(documentId, "CHANGES_REQUESTED");

		uploadVersion(this.member, documentId, pdfFile()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2));
		assertThat(documentRow(documentId).get("status")).isEqualTo("CHANGES_REQUESTED");
	}

	// AC-VER-05: IN_REVIEW·RE_REVIEW·APPROVED는 409, 본문에 상태 값 없음(OQ-2), 행·파일 변화 없음
	@ParameterizedTest
	@ValueSource(strings = { "IN_REVIEW", "RE_REVIEW", "APPROVED" })
	void reviewAndApprovedStatusesRejectNewVersion(String documentStatus) throws Exception {
		Long documentId = createDocument(this.owner, "검토 중", DocumentTestFiles.pdf());
		setStatus(documentId, documentStatus);
		Map<String, Object> documentBefore = documentRow(documentId);
		List<String> filesBefore = DocumentTestSupport.storedFiles();

		String body = uploadVersion(this.owner, documentId, pdfFile()).andExpect(status().isConflict())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode json = this.jsonMapper.readTree(body);
		assertThat(json.propertyNames()).containsExactlyInAnyOrder("code", "message");
		assertThat(json.get("code").asString()).isEqualTo("DOCUMENT_STATUS_CONFLICT");
		assertThat(json.get("message").asString()).isEqualTo("현재 문서 상태에서는 새 버전을 등록할 수 없습니다.");
		assertThat(body).doesNotContain(documentStatus);
		assertThat(versionNumbers(documentId)).containsExactly(1);
		assertThat(documentRow(documentId)).isEqualTo(documentBefore);
		assertThat(DocumentTestSupport.storedFiles()).isEqualTo(filesBefore);
	}

	// AC-VER-12: 비구성원 404 PROJECT_NOT_FOUND, 다른 프로젝트 문서와 없는 문서는 같은 404 본문, 파일 저장 없음
	@Test
	void outsidersAndForeignDocumentsCannotBeDistinguished() throws Exception {
		Long documentId = createDocument(this.owner, "문서", DocumentTestFiles.pdf());
		Long otherProject = createProject(this.outsider, "P2");
		Long foreignDocument = createDocument(this.outsider, "다른 프로젝트 문서", DocumentTestFiles.pdf(), otherProject);
		List<String> filesBefore = DocumentTestSupport.storedFiles();

		uploadVersion(this.outsider, documentId, pdfFile(), this.projectId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		String foreign = uploadVersion(this.owner, foreignDocument, pdfFile(), this.projectId)
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String missing = uploadVersion(this.owner, 999_999L, pdfFile(), this.projectId).andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(foreign).isEqualTo(missing).contains("DOCUMENT_NOT_FOUND");
		uploadVersion(this.owner, documentId, pdfFile(), 999_999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		assertThat(versionNumbers(documentId)).containsExactly(1);
		assertThat(versionNumbers(foreignDocument)).containsExactly(1);
		assertThat(DocumentTestSupport.storedFiles()).isEqualTo(filesBefore);
	}

	// AC-VER-13: 직전 버전과 같은 파일도 새 번호·다른 저장 키로 등록된다 (결정 V7)
	@Test
	void sameFileAgainCreatesNewVersionWithDifferentStorageKey() throws Exception {
		byte[] content = DocumentTestFiles.pdf();
		Long documentId = createDocument(this.owner, "문서", content);

		uploadVersion(this.owner, documentId, file("a.pdf", content)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2));

		Map<String, Object> v1 = versionRow(documentId, 1);
		Map<String, Object> v2 = versionRow(documentId, 2);
		assertThat(v2.get("sha256")).isEqualTo(v1.get("sha256"));
		assertThat(v2.get("storage_key")).isNotEqualTo(v1.get("storage_key"));
		assertThat(DocumentTestSupport.storedFiles()).hasSize(2);
	}

	// AC-VER-07: 파일 누락·빈 파일·텍스트 파트·위장·확장자·파일명 오류는 400 fieldErrors[file], 저장 없음
	@Test
	void invalidFilesAreRejectedLikeDocumentCreation() throws Exception {
		Long documentId = createDocument(this.owner, "문서", DocumentTestFiles.pdf());
		List<String> filesBefore = DocumentTestSupport.storedFiles();

		expectFileError(documentId, null, "파일을 선택해 주세요.");
		expectFileError(documentId, file("a.pdf", new byte[0]), "빈 파일은 업로드할 수 없습니다.");
		expectFileError(documentId, file("a.pdf", "%PDF-1.4 only".getBytes()), "파일이 손상되었거나 형식을 확인할 수 없습니다.");
		expectFileError(documentId, file("a.xlsx", DocumentTestFiles.ooxml(DocumentFileType.DOCX)),
				"파일 확장자와 실제 문서 형식이 일치하지 않습니다.");
		expectFileError(documentId, file("a.pdf.exe", DocumentTestFiles.pdf()), "PDF, DOCX, XLSX, PPTX 파일만 업로드할 수 있습니다.");
		expectFileError(documentId, file("invoice‮fdp.pdf", DocumentTestFiles.pdf()),
				"파일 이름을 확인할 수 없거나 사용할 수 없는 문자가 포함되어 있습니다.");
		expectFileError(documentId, file("가".repeat(252) + ".pdf", DocumentTestFiles.pdf()), "파일 이름은 255자 이하여야 합니다.");
		String textPart = this.mockMvc
			.perform(multipart(versionsUrl(this.projectId, documentId)).param("file", "not a file")
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(textPart).doesNotContain("MultipartFile").doesNotContain("java.");

		assertThat(versionNumbers(documentId)).containsExactly(1);
		assertThat(DocumentTestSupport.storedFiles()).isEqualTo(filesBefore);
	}

	// AC-VER-14: 401, CSRF 403, 비 multipart 415, 숫자가 아닌 ID 400, 정의되지 않은 파트 무시, 저장 없음
	@Test
	void securityAndRequestFormatErrorsFollowExistingContract() throws Exception {
		Long documentId = createDocument(this.owner, "문서", DocumentTestFiles.pdf());
		List<String> filesBefore = DocumentTestSupport.storedFiles();
		String url = versionsUrl(this.projectId, documentId);

		this.mockMvc.perform(multipart(url).file(pdfFile()).with(csrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		this.mockMvc.perform(multipart(url).file(pdfFile()).with(as(this.owner)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		this.mockMvc
			.perform(post(url).with(as(this.owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		this.mockMvc
			.perform(multipart("/api/projects/abc/documents/" + documentId + "/versions").file(pdfFile())
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents/abc/versions").file(pdfFile())
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		assertThat(versionNumbers(documentId)).containsExactly(1);
		assertThat(DocumentTestSupport.storedFiles()).isEqualTo(filesBefore);

		// 버전 번호·업로더·상태·제목 파트는 무시한다
		MockMultipartHttpServletRequestBuilder forged = multipart(url).file(pdfFile());
		forged.param("versionNumber", "7")
			.param("uploadedBy", String.valueOf(this.owner.getId()))
			.param("status", "APPROVED")
			.param("title", "위조 제목");
		this.mockMvc.perform(forged.with(as(this.member)).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.title").value("문서"));
		assertThat(versionRow(documentId, 2).get("uploaded_by")).isEqualTo(this.member.getId());
	}

	private void expectFileError(Long documentId, MockMultipartFile file, String message) throws Exception {
		uploadVersion(this.owner, documentId, file).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'file')].message").value(message));
	}

	private ResultActions uploadVersion(User user, Long documentId, MockMultipartFile file) throws Exception {
		return uploadVersion(user, documentId, file, this.projectId);
	}

	private ResultActions uploadVersion(User user, Long documentId, MockMultipartFile file, Long projectId)
			throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(versionsUrl(projectId, documentId));
		if (file != null) {
			request.file(file);
		}
		return this.mockMvc.perform(request.with(as(user)).with(csrf()));
	}

	private Long createDocument(User user, String title, byte[] content) throws Exception {
		return createDocument(user, title, content, this.projectId);
	}

	private Long createDocument(User user, String title, byte[] content, Long projectId) throws Exception {
		String location = this.mockMvc
			.perform(multipart(documentsUrl(projectId)).file(file("v1.pdf", content))
				.param("title", title)
				.with(as(user))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private Long createProject(User projectOwner, String name) {
		return this.jdbcTemplate.queryForObject("INSERT INTO projects (name, owner_id) VALUES (?, ?) RETURNING id",
				Long.class, name, projectOwner.getId());
	}

	private void addMember(Long project, User user) {
		this.jdbcTemplate.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", project,
				user.getId());
	}

	private void setStatus(Long documentId, String documentStatus) {
		this.jdbcTemplate.update("UPDATE documents SET status = ? WHERE id = ?", documentStatus, documentId);
	}

	private Map<String, Object> documentRow(Long documentId) {
		return this.jdbcTemplate.queryForMap("SELECT * FROM documents WHERE id = ?", documentId);
	}

	private Map<String, Object> versionRow(Long documentId, int versionNumber) {
		return this.jdbcTemplate.queryForMap(
				"SELECT * FROM document_versions WHERE document_id = ? AND version_number = ?", documentId,
				versionNumber);
	}

	private List<Integer> versionNumbers(Long documentId) {
		return this.jdbcTemplate.queryForList(
				"SELECT version_number FROM document_versions WHERE document_id = ? ORDER BY version_number",
				Integer.class, documentId);
	}

	private static String documentsUrl(Long projectId) {
		return "/api/projects/" + projectId + "/documents";
	}

	private static String versionsUrl(Long projectId, Long documentId) {
		return documentsUrl(projectId) + "/" + documentId + "/versions";
	}

	private static MockMultipartFile pdfFile() {
		return file("v.pdf", DocumentTestFiles.pdf());
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "application/octet-stream", content);
	}

	private static String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

}
