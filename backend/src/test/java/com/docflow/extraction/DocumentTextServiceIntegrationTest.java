package com.docflow.extraction;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.docflow.document.DocumentApiHelper;
import com.docflow.document.DocumentTestFiles;
import com.docflow.document.DocumentTestSupport;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.extraction.TestPdfs.TestPage;
import com.docflow.extraction.domain.ExtractionStatus;
import com.docflow.extraction.service.DocumentTextService;
import com.docflow.extraction.service.VersionText;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/** SPEC-10 버전별 추출 저장·재사용 통합 테스트 (실제 PostgreSQL). AC-EXT-09~12. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class DocumentTextServiceIntegrationTest {

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
	private DocumentTextService documentTextService;

	private User owner;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.owner = this.userAccountService.createUser("owner@test.local", "Correct-Horse-Battery-9", "Owner");
		this.projectId = new DocumentApiHelper(this.mockMvc, this.jdbcTemplate).createProject(this.owner, "P1");
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	private Long uploadVersion1(String filename, byte[] content) throws Exception {
		this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents")
				.file(new MockMultipartFile("file", filename, "application/octet-stream", content))
				.param("title", "문서")
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isCreated());
		return this.jdbcTemplate.queryForObject("SELECT max(id) FROM document_versions", Long.class);
	}

	private int rowCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_version_texts", Integer.class);
	}

	// AC-EXT-09: 업로드는 추출하지 않는다. 처음 요청 시 추출·저장하고 이후 저장된 결과를 재사용한다.
	@Test
	void extractsOnceAndReusesStoredResult() throws Exception {
		Long versionId = uploadVersion1("plan.pdf", TestPdfs.pages(TestPage.a4("사업 계획 Plan")));
		assertThat(rowCount()).isZero();
		assertThat(this.documentTextService.findStored(versionId)).isEmpty();

		VersionText first = this.documentTextService.getOrExtract(versionId);
		assertThat(first.status()).isEqualTo(ExtractionStatus.EXTRACTED);
		assertThat(first.text().plainTextWithPageMarkers()).isEqualTo("[p.1]\n사업 계획 Plan");
		String createdAt = this.jdbcTemplate.queryForObject(
				"SELECT created_at::text FROM document_version_texts WHERE version_id = ?", String.class, versionId);

		VersionText second = this.documentTextService.getOrExtract(versionId);
		assertThat(second.text().words()).hasSize(3);
		assertThat(rowCount()).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT created_at::text FROM document_version_texts WHERE version_id = ?", String.class, versionId))
			.isEqualTo(createdAt);
		assertThat(this.jdbcTemplate.queryForMap(
				"SELECT page_count, word_count, char_count FROM document_version_texts WHERE version_id = ?", versionId))
			.containsEntry("page_count", 1)
			.containsEntry("word_count", 3)
			.containsEntry("char_count", 8);
	}

	// AC-EXT-10: 동시에 요청해도 행은 하나이며 모두 같은 결과를 받는다
	@Test
	void concurrentRequestsStoreOneRow() throws Exception {
		Long versionId = uploadVersion1("plan.pdf", TestPdfs.pages(TestPage.a4("동시 요청")));
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Callable<VersionText>> calls = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				calls.add(() -> this.documentTextService.getOrExtract(versionId));
			}
			for (Future<VersionText> future : pool.invokeAll(calls)) {
				assertThat(future.get().status()).isEqualTo(ExtractionStatus.EXTRACTED);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(rowCount()).isEqualTo(1);
	}

	// AC-EXT-11: 실패 상태도 저장해 같은 파일을 반복 처리하지 않는다. 본문은 없다.
	@Test
	void failureStatusesAreStoredWithoutContent() throws Exception {
		Long noText = uploadVersion1("scan.pdf", TestPdfs.noText());
		Long docx = uploadVersion1("a.docx", DocumentTestFiles.valid(DocumentFileType.DOCX));

		assertThat(this.documentTextService.getOrExtract(noText).status()).isEqualTo(ExtractionStatus.NO_TEXT);
		assertThat(this.documentTextService.getOrExtract(docx).status()).isEqualTo(ExtractionStatus.UNSUPPORTED_TYPE);
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM document_version_texts WHERE content IS NULL", Integer.class)).isEqualTo(2);
		assertThat(this.documentTextService.getOrExtract(noText).text()).isNull();
		assertThat(rowCount()).isEqualTo(2);
	}

	// AC-EXT-12: 추출기 버전이 낮은 결과는 다시 추출해 갱신한다
	@Test
	void olderExtractorVersionIsReExtracted() throws Exception {
		Long versionId = uploadVersion1("plan.pdf", TestPdfs.pages(TestPage.a4("버전 갱신")));
		this.documentTextService.getOrExtract(versionId);
		this.jdbcTemplate.update("UPDATE document_version_texts SET extractor_version = 0, status = 'TIMEOUT', content = NULL");
		assertThat(this.documentTextService.findStored(versionId)).isEmpty();

		assertThat(this.documentTextService.getOrExtract(versionId).status()).isEqualTo(ExtractionStatus.EXTRACTED);
		assertThat(this.jdbcTemplate.queryForObject("SELECT extractor_version FROM document_version_texts", Integer.class))
			.isEqualTo(DocumentTextService.EXTRACTOR_VERSION);
	}

}
