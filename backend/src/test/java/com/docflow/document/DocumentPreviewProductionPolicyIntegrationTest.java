package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * #27 운영 정책: prod 프로파일 기본값은 Office 변환 비활성화(LibreOffice를 실행하지 않음)이며 Office 미리보기는
 * {@code 503 DOCUMENT_PREVIEW_UNAVAILABLE}이고, PDF 미리보기와 원본 다운로드는 정상이다.
 */
@SpringBootTest
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@DirtiesContext
class DocumentPreviewProductionPolicyIntegrationTest {

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		DocumentTestSupport.registerStorage(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private com.docflow.document.preview.PreviewProperties previewProperties;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User owner;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.owner = this.userAccountService.createUser("owner@test.local", "Correct-Horse-Battery-9", "Owner");
		this.projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, this.owner.getId());
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	@Test
	void productionDefaultsDisableOfficeConversionAndRequireIsolation() {
		assertThat(this.previewProperties.officeEnabled()).isFalse();
		assertThat(this.previewProperties.requireIsolation()).isTrue();
	}

	// prod 프로파일: CSRF 쿠키 Secure(세션 쿠키와 같은 설정값), 개발 Seeder 미등록
	@Test
	void productionProfileUsesSecureCookiesAndNoDevSeeder(
			@Autowired org.springframework.context.ApplicationContext context) throws Exception {
		// 이 클래스의 다른 테스트가 쓰는 csrf() 테스트 지원은 필터의 저장소를 바꾸므로, 실제 쿠키 저장소 빈을 직접 확인한다.
		var repository = context.getBean(org.springframework.security.web.csrf.CookieCsrfTokenRepository.class);
		var request = new org.springframework.mock.web.MockHttpServletRequest();
		var response = new org.springframework.mock.web.MockHttpServletResponse();
		repository.saveToken(repository.generateToken(request), request, response);
		assertThat(response.getHeader("Set-Cookie")).startsWith("XSRF-TOKEN=").contains("Secure");
		assertThat(context.getBeanNamesForType(com.docflow.auth.dev.DevUserSeeder.class)).isEmpty();
		assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
	}

	@Test
	void officePreviewIsUnavailableButPdfPreviewAndDownloadWork() throws Exception {
		byte[] docx = DocumentPreviewIntegrationTest.fixtureBytes("sample.docx");
		Long docxId = create("a.docx", docx);
		Long pdfId = create("b.pdf", DocumentTestFiles.pdf());

		String body = this.mockMvc.perform(get(url(docxId, "preview")).with(as(this.owner)))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("DOCUMENT_PREVIEW_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("지금은 미리보기를 사용할 수 없습니다. 원본 파일을 내려받아 확인해 주세요."))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("disabled by configuration").doesNotContain("soffice");
		assertThat(this.mockMvc.perform(get(url(docxId, "download")).with(as(this.owner))).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray()).isEqualTo(docx);
		assertThat(this.mockMvc.perform(get(url(pdfId, "preview")).with(as(this.owner))).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray()).isEqualTo(DocumentTestFiles.pdf());
	}

	private Long create(String name, byte[] content) throws Exception {
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents")
				.file(new MockMultipartFile("file", name, "application/octet-stream", content))
				.param("title", "문서")
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private String url(Long documentId, String action) {
		return "/api/projects/" + this.projectId + "/documents/" + documentId + "/versions/1/" + action;
	}

}
