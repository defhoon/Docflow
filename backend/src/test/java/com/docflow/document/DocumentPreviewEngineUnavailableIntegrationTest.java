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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-05 AC-VDL-13: 변환 엔진을 찾을 수 없으면 Office 미리보기는 {@code 503 DOCUMENT_PREVIEW_UNAVAILABLE}이고, PDF 미리보기와
 * 원본 다운로드는 정상이다.
 */
@SpringBootTest(properties = "docflow.preview.soffice-path=/nonexistent/docflow/soffice")
@AutoConfigureMockMvc
@DirtiesContext
class DocumentPreviewEngineUnavailableIntegrationTest {

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
	void officePreviewIsUnavailableButPdfPreviewAndDownloadWork() throws Exception {
		byte[] docx = DocumentPreviewIntegrationTest.fixtureBytes("sample.docx");
		Long docxId = create("a.docx", docx);
		Long pdfId = create("b.pdf", DocumentTestFiles.pdf());

		String body = this.mockMvc.perform(get(url(docxId, "preview")).with(as(this.owner)))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("DOCUMENT_PREVIEW_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("지금은 미리보기를 사용할 수 없습니다. 원본 파일을 내려받아 확인해 주세요."))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("nonexistent").doesNotContain("soffice");
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
