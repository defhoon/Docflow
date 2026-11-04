package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.document.validation.DocumentFileType;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-05 버전 히스토리·원본 다운로드 통합 테스트 (실제 PostgreSQL, 보안 설정 활성, MockMvc). AC-VDL-01~06, 08.
 * <p>
 * 기존 테스트 컨텍스트와 함께 PostgreSQL 기본 연결 상한을 넘지 않도록 클래스가 끝나면 컨텍스트를 닫는다([기존] SPEC-04 §11.5).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class DocumentVersionQueryIntegrationTest {

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

	// AC-VDL-01, 02: 최신순, 최신 표시, 업로더(작성자가 아닌 MEMBER 포함), 내부 정보 없음, 업로더 N+1 없음
	@Test
	void listsVersionsNewestFirstWithUploaderAndNoInternals() throws Exception {
		Long documentId = createDocument(this.owner, file("v1.pdf", DocumentTestFiles.pdf()));
		uploadVersion(this.member, documentId, file("요구사항-v2.docx", DocumentTestFiles.valid(DocumentFileType.DOCX)));
		uploadVersion(this.owner, documentId, file("v3.pdf", DocumentTestFiles.pdf()));
		Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		String body = this.mockMvc.perform(get(versionsUrl(documentId)).with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].versionNumber").value(3))
			.andExpect(jsonPath("$[0].latest").value(true))
			.andExpect(jsonPath("$[0].uploadedBy.id").value(this.owner.getId()))
			.andExpect(jsonPath("$[1].versionNumber").value(2))
			.andExpect(jsonPath("$[1].latest").value(false))
			.andExpect(jsonPath("$[1].originalFilename").value("요구사항-v2.docx"))
			.andExpect(jsonPath("$[1].sizeBytes").value(DocumentTestFiles.valid(DocumentFileType.DOCX).length))
			.andExpect(jsonPath("$[1].uploadedBy.id").value(this.member.getId()))
			.andExpect(jsonPath("$[1].uploadedBy.displayName").value("Member"))
			.andExpect(jsonPath("$[1].uploadedBy.*", hasSize(2)))
			.andExpect(jsonPath("$[1].uploadedAt").exists())
			.andExpect(jsonPath("$[1].*", hasSize(6)))
			.andExpect(jsonPath("$[2].versionNumber").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
		assertThat(body).doesNotContain("documents/")
			.doesNotContain("storage")
			.doesNotContain("sha256")
			.doesNotContain("contentType")
			.doesNotContain("@test.local")
			.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString());
	}

	// AC-VDL-03, 04: 4개 형식 원본 바이트 일치와 다운로드 헤더
	@ParameterizedTest
	@EnumSource(DocumentFileType.class)
	void downloadsOriginalBytesWithSafeHeaders(DocumentFileType type) throws Exception {
		byte[] content = DocumentTestFiles.valid(type);
		String filename = "보고서 원본." + type.extension();
		Long documentId = createDocument(this.owner, file(filename, content));

		MockHttpServletResponse response = download(this.member, documentId, 1).andExpect(status().isOk())
			.andExpect(header().string("Content-Type", type.contentType()))
			.andExpect(header().longValue("Content-Length", content.length))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
			.andReturn()
			.getResponse();

		assertThat(response.getContentAsByteArray()).isEqualTo(content);
		assertThat(response.getHeader("Cache-Control")).contains("no-store");
		assertThat(response.getHeader("Content-Disposition")).startsWith("attachment; ")
			.contains("filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C%20%EC%9B%90%EB%B3%B8." + type.extension());
		assertThat(response.getHeader("Content-Disposition")).doesNotContain("documents/");
	}

	// AC-VDL-03: 이전 버전도 원본 그대로 내려받는다
	@Test
	void downloadsPreviousVersion() throws Exception {
		byte[] v1 = DocumentTestFiles.pdf();
		byte[] v2 = DocumentTestFiles.valid(DocumentFileType.PPTX);
		Long documentId = createDocument(this.owner, file("v1.pdf", v1));
		uploadVersion(this.owner, documentId, file("v2.pptx", v2));

		assertThat(download(this.owner, documentId, 1).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsByteArray()).isEqualTo(v1);
		assertThat(sha256(download(this.owner, documentId, 2).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsByteArray())).isEqualTo(sha256(v2));
	}

	// AC-VDL-05: 미인증 401, 비구성원 404, 다른 프로젝트 문서 404, 다른 문서·없는 버전 404(같은 본문), 숫자 아님 400
	@Test
	void accessAndOwnershipAreCheckedForListAndDownload() throws Exception {
		Long documentId = createDocument(this.owner, file("a.pdf", DocumentTestFiles.pdf()));
		Long otherDocument = createDocument(this.owner, file("b.pdf", DocumentTestFiles.pdf()));
		uploadVersion(this.owner, otherDocument, file("b2.pdf", DocumentTestFiles.pdf()));
		Long foreignProject = createProject(this.outsider, "P2");
		Long foreignDocument = createDocument(this.outsider, file("c.pdf", DocumentTestFiles.pdf()), foreignProject);

		this.mockMvc.perform(get(versionsUrl(documentId))).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		this.mockMvc.perform(get(downloadUrl(this.projectId, documentId, 1))).andExpect(status().isUnauthorized());

		for (String url : List.of(versionsUrl(documentId), downloadUrl(this.projectId, documentId, 1))) {
			this.mockMvc.perform(get(url).with(as(this.outsider))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		}
		String foreignList = this.mockMvc
			.perform(get(versionsUrl(foreignDocument)).with(as(this.owner)))
			.andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();
		String missingList = this.mockMvc.perform(get(versionsUrl(999_999L)).with(as(this.owner)))
			.andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();
		assertThat(foreignList).isEqualTo(missingList).contains("DOCUMENT_NOT_FOUND");
		download(this.owner, foreignDocument, 1).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

		// 다른 문서에는 v2가 있지만 이 문서에는 없다
		String otherDocumentsVersion = download(this.owner, documentId, 2).andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();
		String zero = download(this.owner, documentId, 0).andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();
		String negative = download(this.owner, documentId, -1).andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();
		assertThat(otherDocumentsVersion).isEqualTo(zero).isEqualTo(negative).contains("DOCUMENT_VERSION_NOT_FOUND");

		this.mockMvc.perform(get(downloadUrl(this.projectId, documentId, 1).replace("/versions/1/", "/versions/abc/"))
			.with(as(this.owner))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/documents/abc/versions").with(as(this.owner)))
			.andExpect(status().isBadRequest());
	}

	// AC-VDL-06: 문서 상태와 관계없이 조회·다운로드
	@ParameterizedTest
	@ValueSource(strings = { "DRAFT", "IN_REVIEW", "CHANGES_REQUESTED", "RE_REVIEW", "APPROVED" })
	void everyStatusAllowsListAndDownload(String documentStatus) throws Exception {
		Long documentId = createDocument(this.owner, file("a.pdf", DocumentTestFiles.pdf()));
		this.jdbcTemplate.update("UPDATE documents SET status = ? WHERE id = ?", documentStatus, documentId);

		this.mockMvc.perform(get(versionsUrl(documentId)).with(as(this.member))).andExpect(status().isOk());
		download(this.member, documentId, 1).andExpect(status().isOk());
	}

	// AC-VDL-08: 원본 누락·크기 불일치 → 500 DOCUMENT_FILE_UNAVAILABLE(JSON, 내부 정보 없음), 원인 로그
	@Test
	void missingOrResizedStoredFileIsUnavailable(CapturedOutput output) throws Exception {
		Long documentId = createDocument(this.owner, file("a.pdf", DocumentTestFiles.pdf()));
		String key = this.jdbcTemplate.queryForObject("SELECT storage_key FROM document_versions WHERE document_id = ?",
				String.class, documentId);
		Files.write(DocumentTestSupport.STORAGE_ROOT.resolve(key), new byte[] { 1, 2, 3 });

		String resized = download(this.owner, documentId, 1).andExpect(status().isInternalServerError())
			.andExpect(header().string("Content-Type", "application/json"))
			.andExpect(jsonPath("$.code").value("DOCUMENT_FILE_UNAVAILABLE"))
			.andReturn().getResponse().getContentAsString();
		Files.delete(DocumentTestSupport.STORAGE_ROOT.resolve(key));
		String missing = download(this.owner, documentId, 1).andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("DOCUMENT_FILE_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("파일을 불러올 수 없습니다. 관리자에게 문의해 주세요."))
			.andReturn().getResponse().getContentAsString();

		for (String body : List.of(resized, missing)) {
			assertThat(body).doesNotContain(key).doesNotContain("documents/")
				.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString());
		}
		List<String> causeLines = output.getOut()
			.lines()
			.filter((line) -> line.contains("Stored document file unavailable"))
			.toList();
		assertThat(causeLines).hasSize(2);
		assertThat(causeLines.get(0)).contains("projectId=" + this.projectId, "documentId=" + documentId,
				"versionNumber=1", "storageKey=" + key, "reason=size mismatch");
		assertThat(causeLines.get(1)).contains("reason=missing");
		assertThat(causeLines).allSatisfy((line) -> assertThat(line).doesNotContain("a.pdf")
			.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString()));
	}

	// SPEC-05 §6.4: Accept 값과 관계없이 파일을 반환하고, 오류는 JSON으로 응답한다
	@ParameterizedTest
	@ValueSource(strings = { "application/json", "*/*",
			"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "" })
	void downloadIgnoresAcceptHeader(String accept) throws Exception {
		byte[] content = DocumentTestFiles.pdf();
		Long documentId = createDocument(this.owner, file("a.pdf", content));

		var ok = get(downloadUrl(this.projectId, documentId, 1)).with(as(this.owner));
		var missing = get(downloadUrl(this.projectId, documentId, 9)).with(as(this.owner));
		if (!accept.isEmpty()) {
			ok.header("Accept", accept);
			missing.header("Accept", accept);
		}
		assertThat(this.mockMvc.perform(ok).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
			.isEqualTo(content);
		this.mockMvc.perform(missing).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_FOUND"));
	}

	private ResultActions download(User user, Long documentId, int versionNumber) throws Exception {
		return this.mockMvc.perform(get(downloadUrl(this.projectId, documentId, versionNumber)).with(as(user)));
	}

	private void uploadVersion(User user, Long documentId, MockMultipartFile file) throws Exception {
		this.mockMvc.perform(multipart(versionsUrl(documentId)).file(file).with(as(user)).with(csrf()))
			.andExpect(status().isCreated());
	}

	private Long createDocument(User user, MockMultipartFile file) throws Exception {
		return createDocument(user, file, this.projectId);
	}

	private Long createDocument(User user, MockMultipartFile file, Long projectId) throws Exception {
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + projectId + "/documents").file(file)
				.param("title", "문서")
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

	private String versionsUrl(Long documentId) {
		return "/api/projects/" + this.projectId + "/documents/" + documentId + "/versions";
	}

	private static String downloadUrl(Long projectId, Long documentId, int versionNumber) {
		return "/api/projects/" + projectId + "/documents/" + documentId + "/versions/" + versionNumber + "/download";
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "application/octet-stream", content);
	}

	private static String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

}
