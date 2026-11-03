package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.docflow.document.validation.DocumentFileType;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-03 문서 API 통합 테스트 (실제 PostgreSQL, 보안 설정 활성 상태, MockMvc multipart).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class DocumentApiIntegrationTest {

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

	@Autowired
	private EntityManagerFactory entityManagerFactory;

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
		this.jdbcTemplate.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", this.projectId,
				this.member.getId());
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-DOC-01, 02, 03, 18, 28: 4개 형식 등록, DRAFT, v1, 서버 판별 Content Type, 크기·SHA-256, 내부 정보 미노출
	@ParameterizedTest
	@EnumSource(DocumentFileType.class)
	void ownerRegistersEachFormat(DocumentFileType type) throws Exception {
		byte[] content = DocumentTestFiles.valid(type);
		String filename = "보고서." + type.name().toLowerCase();

		String body = upload(this.owner, Map.of("title", "  요구사항 명세서  ", "description", "1차 초안"),
				file(filename, "application/octet-stream", content))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", matchesPattern("/api/projects/" + this.projectId + "/documents/\\d+")))
			.andExpect(jsonPath("$.title").value("요구사항 명세서"))
			.andExpect(jsonPath("$.description").value("1차 초안"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.createdBy.id").value(this.owner.getId()))
			.andExpect(jsonPath("$.createdBy.displayName").value("Owner"))
			.andExpect(jsonPath("$.createdBy.*", hasSize(2)))
			.andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.+Z")))
			.andExpect(jsonPath("$.updatedAt").exists())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(1))
			.andExpect(jsonPath("$.latestVersion.originalFilename").value(filename))
			.andExpect(jsonPath("$.latestVersion.sizeBytes").value(content.length))
			.andExpect(jsonPath("$.latestVersion.uploadedAt").exists())
			.andExpect(jsonPath("$.latestVersion.*", hasSize(4)))
			.andExpect(jsonPath("$.*", hasSize(8)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		Map<String, Object> row = this.jdbcTemplate.queryForMap(
				"SELECT d.status, d.created_by, v.version_number, v.content_type, v.size_bytes, v.sha256, v.storage_key, v.uploaded_by, v.original_filename "
						+ "FROM documents d JOIN document_versions v ON v.document_id = d.id");
		assertThat(row.get("status")).isEqualTo("DRAFT");
		assertThat(row.get("created_by")).isEqualTo(this.owner.getId());
		assertThat(row.get("uploaded_by")).isEqualTo(this.owner.getId());
		assertThat(row.get("version_number")).isEqualTo(1);
		assertThat(row.get("content_type")).isEqualTo(type.contentType());
		assertThat(row.get("size_bytes")).isEqualTo((long) content.length);
		assertThat(row.get("sha256")).isEqualTo(sha256(content));
		assertThat(row.get("original_filename")).isEqualTo(filename);
		String storageKey = (String) row.get("storage_key");
		assertThat(storageKey).matches("documents/[0-9a-f-]{36}");
		assertThat(Files.readAllBytes(DocumentTestSupport.STORAGE_ROOT.resolve(storageKey))).isEqualTo(content);
		assertThat(DocumentTestSupport.storedFiles()).containsExactly(storageKey);
		assertThat(body).doesNotContain(storageKey)
			.doesNotContain("sha256")
			.doesNotContain(sha256(content))
			.doesNotContain("contentType")
			.doesNotContain("storage")
			.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString())
			.doesNotContain("@test.local");
	}

	// AC-DOC-01: MEMBER도 등록 가능, 작성자는 세션 사용자
	@Test
	void memberCanRegisterAndBecomesAuthor() throws Exception {
		upload(this.member, Map.of("title", "멤버 문서"), pdfFile()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdBy.id").value(this.member.getId()));
	}

	// §6.3: 요청의 작성자·상태·버전 파트는 무시
	@Test
	void ignoresForgedAuthorStatusAndVersionParts() throws Exception {
		upload(this.member, Map.of("title", "위조", "createdBy", String.valueOf(this.owner.getId()), "status",
				"APPROVED", "versionNumber", "7"), pdfFile())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdBy.id").value(this.member.getId()))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(1));
	}

	// AC-DOC-15: 비구성원은 등록·목록·상세 모두 404, 파일을 저장하지 않는다
	@Test
	void outsiderGetsProjectNotFoundAndNothingIsStored() throws Exception {
		Long documentId = createDocument(this.owner, "기존 문서");
		DocumentTestSupport.cleanStorage();

		upload(this.outsider, Map.of("title", "침입"), pdfFile()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		this.mockMvc.perform(get(documentsUrl(this.projectId)).with(as(this.outsider)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + documentId).with(as(this.outsider)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		assertThat(documentCount()).isEqualTo(1);
		assertThat(DocumentTestSupport.storedFiles()).isEmpty();
	}

	// AC-DOC-15: 존재하지 않는 프로젝트도 같은 404
	@Test
	void missingProjectIsIndistinguishableFromForeignProject() throws Exception {
		upload(this.owner, Map.of("title", "x"), pdfFile(), Long.MAX_VALUE).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
	}

	// AC-DOC-16: IDOR — 다른 프로젝트의 문서 ID는 존재하지 않는 문서와 같은 404
	@Test
	void documentOfAnotherProjectIsNotFound() throws Exception {
		Long otherProject = createProject(this.owner, "P2");
		Long foreignDocument = createDocument(this.owner, "다른 프로젝트 문서", otherProject);

		String foreign = this.mockMvc
			.perform(get(documentsUrl(this.projectId) + "/" + foreignDocument).with(as(this.owner)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String missing = this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + Long.MAX_VALUE).with(as(this.owner)))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(foreign).isEqualTo(missing);

		// 멤버도 자기 프로젝트 경로로 다른 프로젝트 문서를 볼 수 없다
		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + foreignDocument).with(as(this.member)))
			.andExpect(status().isNotFound());
	}

	// AC-DOC-18: 상세 조회
	@Test
	void memberReadsDocumentDetail() throws Exception {
		Long documentId = createDocument(this.owner, "상세");

		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + documentId).with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(documentId))
			.andExpect(jsonPath("$.title").value("상세"))
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.createdBy.displayName").value("Owner"))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(1))
			.andExpect(jsonPath("$.latestVersion.originalFilename").value("a.pdf"));
	}

	// AC-DOC-17: 목록 정렬(created_at DESC, id DESC), 최신 버전 번호, N+1 없음
	@Test
	void listsDocumentsNewestFirstWithSingleQuery() throws Exception {
		Long first = createDocument(this.owner, "첫째");
		Long second = createDocument(this.member, "둘째");
		Long third = createDocument(this.owner, "셋째");
		Long sameTimeLowerId = createDocument(this.owner, "같은 시각 A");
		Long sameTimeHigherId = createDocument(this.owner, "같은 시각 B");
		Timestamp same = Timestamp.from(Instant.parse("2030-01-01T00:00:00Z"));
		this.jdbcTemplate.update("UPDATE documents SET created_at = ? WHERE id IN (?, ?)", same, sameTimeLowerId,
				sameTimeHigherId);
		this.jdbcTemplate.update("UPDATE documents SET created_at = ? WHERE id = ?",
				Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), third);
		// 같은 문서에 v2 행을 직접 추가해 MAX(version_number)를 확인한다 (SPEC-04 이전이라 API 없음)
		this.jdbcTemplate.update("""
				INSERT INTO document_versions (document_id, version_number, original_filename, content_type, size_bytes,
				    storage_key, sha256, uploaded_by)
				SELECT document_id, 2, original_filename, content_type, size_bytes, storage_key || '-v2', sha256, uploaded_by
				FROM document_versions WHERE document_id = ?""", second);

		Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		this.mockMvc.perform(get(documentsUrl(this.projectId)).with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(5)))
			.andExpect(jsonPath("$[0].id").value(sameTimeHigherId))
			.andExpect(jsonPath("$[1].id").value(sameTimeLowerId))
			.andExpect(jsonPath("$[2].id").value(second))
			.andExpect(jsonPath("$[2].latestVersionNumber").value(2))
			.andExpect(jsonPath("$[2].createdBy.displayName").value("Member"))
			.andExpect(jsonPath("$[3].id").value(first))
			.andExpect(jsonPath("$[3].latestVersionNumber").value(1))
			.andExpect(jsonPath("$[4].id").value(third))
			.andExpect(jsonPath("$[0].*", hasSize(6)))
			.andExpect(jsonPath("$[0].createdBy.*", hasSize(2)));
		// 접근 확인 1 + 목록 1
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + second).with(as(this.owner)))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2));
	}

	@Test
	void emptyListForProjectWithoutDocuments() throws Exception {
		this.mockMvc.perform(get(documentsUrl(this.projectId)).with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	// AC-DOC-10: 제목·설명 검증과 정규화
	@Test
	void validatesTitleAndDescription() throws Exception {
		expectFieldError(Map.of("title", "   "), pdfFile(), "title", "문서 제목을 입력해 주세요.");
		expectFieldError(Map.of(), pdfFile(), "title", "문서 제목을 입력해 주세요.");
		expectFieldError(Map.of("title", "가".repeat(201)), pdfFile(), "title", "문서 제목은 200자 이하로 입력해 주세요.");
		expectFieldError(Map.of("title", "a‮b"), pdfFile(), "title", "문서 제목에 사용할 수 없는 문자가 포함되어 있습니다.");
		expectFieldError(Map.of("title", "t", "description", "가".repeat(1001)), pdfFile(), "description",
				"설명은 1000자 이하로 입력해 주세요.");
		expectFieldError(Map.of("title", "t", "description", "a\u0007b"), pdfFile(), "description",
				"설명에 사용할 수 없는 문자가 포함되어 있습니다.");

		upload(this.owner, Map.of("title", "가".repeat(200), "description", "   "), pdfFile())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").doesNotExist());
		assertThat(this.jdbcTemplate.queryForObject("SELECT description FROM documents", String.class)).isNull();
	}

	// AC-DOC-06: 파일 누락·빈 파일
	@Test
	void rejectsMissingAndEmptyFile() throws Exception {
		expectFieldError(Map.of("title", "t"), null, "file", "파일을 선택해 주세요.");
		expectFieldError(Map.of("title", "t"), file("a.pdf", "application/pdf", new byte[0]), "file",
				"빈 파일은 업로드할 수 없습니다.");
		assertNothingPersisted();
	}

	// file 파트를 파일이 아닌 텍스트 값으로 보내도 내부 타입 정보를 노출하지 않는다
	@Test
	void fileSentAsTextFieldIsValidationError() throws Exception {
		String body = this.mockMvc
			.perform(multipart(documentsUrl(this.projectId)).param("title", "t")
				.param("file", "not a file")
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("MultipartFile").doesNotContain("java.");
	}

	// AC-DOC-04: 형식 위장·확장자·손상 파일 거부, 저장·DB 없음
	@Test
	void rejectsDisguisedAndUnsupportedFiles() throws Exception {
		expectFieldError(Map.of("title", "t"), file("a.pdf", "application/pdf", "%PDF-1.4 only".getBytes()), "file",
				"파일이 손상되었거나 형식을 확인할 수 없습니다.");
		expectFieldError(Map.of("title", "t"), file("a.docx", MediaType.APPLICATION_PDF_VALUE, DocumentTestFiles.pdf()),
				"file", "파일이 손상되었거나 형식을 확인할 수 없습니다.");
		expectFieldError(Map.of("title", "t"),
				file("a.xlsx", "application/octet-stream", DocumentTestFiles.ooxml(DocumentFileType.DOCX)), "file",
				"파일 확장자와 실제 문서 형식이 일치하지 않습니다.");
		expectFieldError(Map.of("title", "t"), file("a.pdf.exe", "application/pdf", DocumentTestFiles.pdf()), "file",
				"PDF, DOCX, XLSX, PPTX 파일만 업로드할 수 있습니다.");
		expectFieldError(Map.of("title", "t"), file("a.txt", "text/plain", "hello".getBytes()), "file",
				"PDF, DOCX, XLSX, PPTX 파일만 업로드할 수 있습니다.");
		// 클라이언트 Content-Type은 판별에 쓰지 않는다
		upload(this.owner, Map.of("title", "t"), file("a.pdf", "text/plain", DocumentTestFiles.pdf()))
			.andExpect(status().isCreated());
		assertThat(this.jdbcTemplate.queryForObject("SELECT content_type FROM document_versions", String.class))
			.isEqualTo("application/pdf");
		assertThat(documentCount()).isEqualTo(1);
		assertThat(DocumentTestSupport.storedFiles()).hasSize(1).allMatch((path) -> path.startsWith("documents/"));
	}

	// AC-DOC-10: 파일명 정규화·거부
	@Test
	void normalizesAndValidatesOriginalFilename() throws Exception {
		upload(this.owner, Map.of("title", "t"), file("..\\..\\etc/  계획서.PDF ", "application/pdf",
				DocumentTestFiles.pdf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.latestVersion.originalFilename").value("계획서.PDF"));
		expectFieldError(Map.of("title", "t"), file("invoice‮fdp.pdf", "application/pdf", DocumentTestFiles.pdf()),
				"file", "파일 이름을 확인할 수 없거나 사용할 수 없는 문자가 포함되어 있습니다.");
		expectFieldError(Map.of("title", "t"), file("가".repeat(252) + ".pdf", "application/pdf",
				DocumentTestFiles.pdf()), "file", "파일 이름은 255자 이하여야 합니다.");
	}

	// AC-DOC-20: 미인증 401, CSRF 없음 403, multipart 아님 415, 숫자가 아닌 ID 400
	@Test
	void securityAndRequestFormatErrors() throws Exception {
		this.mockMvc.perform(multipart(documentsUrl(this.projectId)).file(pdfFile()).param("title", "t").with(csrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		this.mockMvc.perform(get(documentsUrl(this.projectId))).andExpect(status().isUnauthorized());
		this.mockMvc.perform(multipart(documentsUrl(this.projectId)).file(pdfFile()).param("title", "t").with(as(this.owner)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		this.mockMvc
			.perform(post(documentsUrl(this.projectId)).with(as(this.owner))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"t\"}"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		this.mockMvc.perform(get("/api/projects/abc/documents").with(as(this.owner)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/abc").with(as(this.owner)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		assertNothingPersisted();
	}

	// AC-DOC-19: 제거된 구성원의 문서는 유지되고 작성자 이름이 보이며, 제거된 사용자는 접근할 수 없다
	@Test
	void documentsOfRemovedMemberRemainAndRemovedUserLosesAccess() throws Exception {
		Long documentId = createDocument(this.member, "멤버가 올린 문서");
		this.jdbcTemplate.update("DELETE FROM project_members WHERE project_id = ? AND user_id = ?", this.projectId,
				this.member.getId());

		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + documentId).with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.createdBy.displayName").value("Member"));
		this.mockMvc.perform(get(documentsUrl(this.projectId)).with(as(this.owner)))
			.andExpect(jsonPath("$[0].createdBy.id").value(this.member.getId()));
		this.mockMvc.perform(get(documentsUrl(this.projectId) + "/" + documentId).with(as(this.member)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		upload(this.member, Map.of("title", "제거 후"), pdfFile()).andExpect(status().isNotFound());
		assertThat(documentCount()).isEqualTo(1);
	}

	// AC-DOC-21: 같은 요청을 다시 보내면 별도 문서와 별도 저장 키가 생긴다
	@Test
	void resubmissionCreatesSeparateDocument() throws Exception {
		createDocument(this.owner, "같은 제목");
		createDocument(this.owner, "같은 제목");

		assertThat(documentCount()).isEqualTo(2);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(DISTINCT storage_key) FROM document_versions",
				Integer.class))
			.isEqualTo(2);
		assertThat(DocumentTestSupport.storedFiles()).hasSize(2);
	}

	private void expectFieldError(Map<String, String> params, MockMultipartFile file, String field, String message)
			throws Exception {
		upload(this.owner, params, file).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')].message").value(message));
	}

	private void assertNothingPersisted() {
		assertThat(documentCount()).isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions", Integer.class)).isZero();
		assertThat(DocumentTestSupport.storedFiles()).isEmpty();
	}

	private ResultActions upload(User user, Map<String, String> params, MockMultipartFile file) throws Exception {
		return upload(user, params, file, this.projectId);
	}

	private ResultActions upload(User user, Map<String, String> params, MockMultipartFile file, Long projectId)
			throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(documentsUrl(projectId));
		if (file != null) {
			request.file(file);
		}
		params.forEach(request::param);
		return this.mockMvc.perform(request.with(as(user)).with(csrf()));
	}

	private Long createDocument(User user, String title) throws Exception {
		return createDocument(user, title, this.projectId);
	}

	private Long createDocument(User user, String title, Long projectId) throws Exception {
		String location = upload(user, Map.of("title", title), pdfFile(), projectId).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private Long createProject(User projectOwner, String name) {
		return this.jdbcTemplate.queryForObject("INSERT INTO projects (name, owner_id) VALUES (?, ?) RETURNING id",
				Long.class, name, projectOwner.getId());
	}

	private int documentCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class);
	}

	private static String documentsUrl(Long projectId) {
		return "/api/projects/" + projectId + "/documents";
	}

	private static MockMultipartFile pdfFile() {
		return file("a.pdf", "application/pdf", DocumentTestFiles.pdf());
	}

	private static MockMultipartFile file(String name, String contentType, byte[] content) {
		return new MockMultipartFile("file", name, contentType, content);
	}

	private static String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

}
