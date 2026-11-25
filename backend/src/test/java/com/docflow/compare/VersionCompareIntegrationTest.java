package com.docflow.compare;

import static com.docflow.document.DocumentTestSupport.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.document.DocumentApiHelper;
import com.docflow.document.DocumentTestFiles;
import com.docflow.document.DocumentTestSupport;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.extraction.TestPdfs;
import com.docflow.extraction.TestPdfs.TestPage;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/** SPEC-12 버전 비교 API 통합 테스트 (실제 PostgreSQL). AC-CMP-09~15. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class VersionCompareIntegrationTest {

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

	private User member;

	private User outsider;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		User owner = this.userAccountService.createUser("owner@test.local", "Correct-Horse-Battery-9", "Owner");
		this.member = this.userAccountService.createUser("member@test.local", "Correct-Horse-Battery-9", "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", "Correct-Horse-Battery-9", "Out");
		DocumentApiHelper api = new DocumentApiHelper(this.mockMvc, this.jdbcTemplate);
		this.projectId = api.createProject(owner, "P1");
		api.addMember(this.projectId, this.member);
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	private Long createDocument(byte[] v1, String name) throws Exception {
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents")
				.file(new MockMultipartFile("file", name, "application/octet-stream", v1))
				.param("title", "문서")
				.with(as(this.member))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private void addVersion(Long documentId, byte[] content, String name) throws Exception {
		this.mockMvc
			.perform(multipart(DocumentApiHelper.documentUrl(this.projectId, documentId) + "/versions")
				.file(new MockMultipartFile("file", name, "application/octet-stream", content))
				.with(as(this.member))
				.with(csrf()))
			.andExpect(status().isCreated());
	}

	private ResultActions compare(User user, Long documentId, String query) throws Exception {
		return this.mockMvc
			.perform(get(DocumentApiHelper.documentUrl(this.projectId, documentId) + "/compare?" + query).with(as(user)));
	}

	private static byte[] v1() {
		return TestPdfs.pages(TestPage.a4("사업 계획서", "", "예산은 1,200만원 입니다", "", "출시일은 12월입니다"));
	}

	private static byte[] v2() {
		return TestPdfs.pages(TestPage.a4("사업 계획서", "", "예산은 1,500만원 입니다", "", "보안 점검을 추가합니다"),
				TestPage.a4("출시일은 12월입니다"));
	}

	// AC-CMP-09: 두 PDF 버전 비교 결과(유형별 개수, 페이지, 좌표, 버전 정보)
	@Test
	void comparesTwoVersions() throws Exception {
		Long documentId = createDocument(v1(), "v1.pdf");
		addVersion(documentId, v2(), "v2.pdf");
		compare(this.member, documentId, "base=1&target=2").andExpect(status().isOk())
			.andExpect(jsonPath("$.base.versionNumber").value(1))
			.andExpect(jsonPath("$.target.pageCount").value(2))
			.andExpect(jsonPath("$.target.pages[0].width").value(595.0))
			.andExpect(jsonPath("$.result.identical").value(false))
			.andExpect(jsonPath("$.result.counts.added").value(1))
			.andExpect(jsonPath("$.result.counts.deleted").value(0))
			.andExpect(jsonPath("$.result.counts.modified").value(1))
			.andExpect(jsonPath("$.result.changes[0].type").value("MODIFIED"))
			.andExpect(jsonPath("$.result.changes[0].base.text").value("1,200만원"))
			.andExpect(jsonPath("$.result.changes[0].target.text").value("1,500만원"))
			.andExpect(jsonPath("$.result.changes[0].target.boxes[0].page").value(1))
			.andExpect(jsonPath("$.result.changes[0].target.location").value("FULL"))
			.andExpect(jsonPath("$.result.changes[1].type").value("ADDED"))
			.andExpect(jsonPath("$.result.changes[1].target.text").value("보안 점검을 추가합니다"));
	}

	// AC-CMP-10: 같은 내용이면 변경 없음
	@Test
	void identicalVersions() throws Exception {
		Long documentId = createDocument(v1(), "v1.pdf");
		addVersion(documentId, v1(), "v1-copy.pdf");
		compare(this.member, documentId, "base=1&target=2").andExpect(status().isOk())
			.andExpect(jsonPath("$.result.identical").value(true))
			.andExpect(jsonPath("$.result.changes.length()").value(0));
	}

	// AC-CMP-11: 외부인 차단, 다른 문서의 버전 번호는 찾을 수 없음
	@Test
	void accessAndOwnershipAreChecked() throws Exception {
		Long documentId = createDocument(v1(), "v1.pdf");
		addVersion(documentId, v2(), "v2.pdf");
		Long other = createDocument(v1(), "other.pdf");
		addVersion(other, v2(), "o2.pdf");
		addVersion(other, v2(), "o3.pdf");
		compare(this.outsider, documentId, "base=1&target=2").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		compare(this.member, documentId, "base=1&target=3").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_FOUND"));
		compare(this.member, 999_999L, "base=1&target=2").andExpect(status().isNotFound());
	}

	// AC-CMP-12: 같은 버전·잘못된 요청
	@Test
	void invalidRequests() throws Exception {
		Long documentId = createDocument(v1(), "v1.pdf");
		compare(this.member, documentId, "base=1&target=1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMPARE_SAME_VERSION"));
		compare(this.member, documentId, "base=1").andExpect(status().isBadRequest());
	}

	// AC-CMP-13: PDF가 아닌 버전, 텍스트 없는 버전
	@Test
	void unsupportedAndTextlessVersions() throws Exception {
		Long documentId = createDocument(v1(), "v1.pdf");
		addVersion(documentId, DocumentTestFiles.valid(DocumentFileType.DOCX), "v2.docx");
		addVersion(documentId, TestPdfs.noText(), "scan.pdf");
		compare(this.member, documentId, "base=1&target=2").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("COMPARE_UNSUPPORTED_TYPE"));
		compare(this.member, documentId, "base=1&target=3").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("COMPARE_TEXT_UNAVAILABLE"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("target"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("NO_TEXT"));
	}

	// AC-CMP-14: 최종 확정 문서도 비교할 수 있다(읽기 전용 조회)
	@Test
	void finalizedDocumentCanBeCompared() throws Exception {
		Long documentId = createDocument(v1(), "v1.pdf");
		addVersion(documentId, v2(), "v2.pdf");
		DocumentTestSupport.finalizeDirectly(this.jdbcTemplate, documentId);
		compare(this.member, documentId, "base=2&target=1").andExpect(status().isOk())
			.andExpect(jsonPath("$.base.versionNumber").value(2))
			.andExpect(jsonPath("$.result.counts.deleted").value(1));
	}

}
