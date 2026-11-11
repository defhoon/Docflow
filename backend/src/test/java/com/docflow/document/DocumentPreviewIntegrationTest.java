package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.document.preview.DocumentPreviewConverter;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-05 미리보기 API 통합 테스트: 실제 PostgreSQL, 보안 설정 활성, 실제 LibreOffice 변환 (AC-VDL-02, 05, 10~14, 17 일부).
 * LibreOffice가 없으면 실패한다(결정 A4). 반환된 PDF는 PDFBox로 열어 페이지 수와 형식별 표식 텍스트를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class DocumentPreviewIntegrationTest {

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

	@MockitoSpyBean
	private DocumentPreviewConverter converter;

	private User owner;

	private User member;

	private User outsider;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		clearInvocations(this.converter);
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.member = this.userAccountService.createUser("member@test.local", PASSWORD, "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", PASSWORD, "Outsider");
		this.projectId = createProject(this.owner);
		this.jdbcTemplate.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", this.projectId,
				this.member.getId());
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-VDL-10, 11, 12, 17: 4개 형식 미리보기 결과가 유효한 PDF, Office는 변환 1회 후 캐시, 원본·DB 불변, 기존 보안 헤더 유지
	@Test
	void previewsAllFourFormatsAsValidPdfWithoutTouchingOriginals() throws Exception {
		byte[] pdf = DocumentTestFiles.pdf();
		Long pdfDoc = createDocument(file("원본.pdf", pdf));
		Long docx = createDocument(fixture("요구사항.docx", "sample.docx"));
		Long xlsx = createDocument(fixture("예산.xlsx", "sample.xlsx"));
		Long pptx = createDocument(fixture("발표.pptx", "sample.pptx"));
		Map<String, Object> rowsBefore = Map.of("versions",
				this.jdbcTemplate.queryForList("SELECT * FROM document_versions ORDER BY id"), "documents",
				this.jdbcTemplate.queryForList("SELECT * FROM documents ORDER BY id"));
		List<String> originalHashes = originalHashes();

		// PDF 원본: 변환 없이 같은 바이트, 첫 요청부터 200
		MockHttpServletResponse pdfPreview = preview(this.member, pdfDoc, 1).andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andReturn().getResponse();
		assertThat(pdfPreview.getContentAsByteArray()).isEqualTo(pdf);

		// Office: 202 PENDING 후 200, 유효한 PDF
		preview(this.member, docx, 1).andExpect(status().isAccepted())
			.andExpect(header().string("Retry-After", "2"))
			.andExpect(jsonPath("$.status").value("PENDING"));
		assertPdf(awaitPreview(docx), "DOCFLOW-PREVIEW-DOCX-1", "요구사항.pdf");
		assertPdf(awaitPreview(xlsx), "DOCFLOW-PREVIEW-XLSX-1", "예산.pdf");
		MockHttpServletResponse pptxPreview = awaitPreview(pptx);
		assertPdf(pptxPreview, "DOCFLOW-PREVIEW-PPTX-1", "발표.pdf");
		assertThat(pptxPreview.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(pptxPreview.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(pptxPreview.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
		assertThat(pptxPreview.getHeader("Cache-Control")).contains("no-store");

		// 두 번째 요청은 캐시로 즉시 200, 변환은 Office 3건 각 1회
		preview(this.owner, docx, 1).andExpect(status().isOk());
		verify(this.converter, times(3)).convertToPdf(any(), any(), any(), any());

		// 원본 파일·DB 행 불변, 결과는 previews/cache에만, 작업 디렉터리 없음
		assertThat(originalHashes()).isEqualTo(originalHashes);
		assertThat(Map.of("versions", this.jdbcTemplate.queryForList("SELECT * FROM document_versions ORDER BY id"),
				"documents", this.jdbcTemplate.queryForList("SELECT * FROM documents ORDER BY id")))
			.isEqualTo(rowsBefore);
		assertThat(filesUnder("previews/cache")).hasSize(3).allMatch((name) -> name.matches("[0-9a-f]{64}\\.pdf"));
		assertThat(filesUnder("previews/work")).isEmpty();
	}

	// AC-VDL-13: 변환할 수 없는 문서 → 422, 원본 다운로드는 정상, 작업 파일 없음
	@Test
	void unconvertibleDocumentFailsButOriginalDownloadStillWorks() throws Exception {
		byte[] broken = fixtureBytes("broken.docx");
		Long documentId = createDocument(file("손상.docx", broken));

		preview(this.owner, documentId, 1).andExpect(status().isAccepted());
		String body = awaitStatus(documentId, 422).getContentAsString();

		assertThat(body).contains("DOCUMENT_PREVIEW_FAILED").contains("미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요.")
			.doesNotContain("previews").doesNotContain("LibreOffice").doesNotContain("documents/");
		assertThat(this.mockMvc.perform(get(url(documentId, 1, "download")).with(as(this.owner)))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(broken);
		assertThat(filesUnder("previews/work")).isEmpty();
		assertThat(filesUnder("previews/cache")).isEmpty();
	}

	// AC-VDL-05, 02: 미리보기 권한·소유 확인은 캐시가 있어도 같고, 오류에 내부 정보가 없다
	@Test
	void previewChecksAccessEvenWhenCached() throws Exception {
		Long documentId = createDocument(fixture("a.docx", "sample.docx"));
		awaitPreview(documentId);
		Long otherDocument = createDocument(file("b.pdf", DocumentTestFiles.pdf()));
		Long foreignProject = createProject(this.outsider);
		Long foreignDocument = createDocument(file("c.pdf", DocumentTestFiles.pdf()), foreignProject, this.outsider);

		this.mockMvc.perform(get(url(documentId, 1, "preview"))).andExpect(status().isUnauthorized());
		preview(this.outsider, documentId, 1).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		preview(this.owner, foreignDocument, 1).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		preview(this.owner, otherDocument, 2).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_FOUND"));
		preview(this.owner, documentId, 0).andExpect(status().isNotFound());
		this.mockMvc.perform(get(url(documentId, 1, "preview").replace("/1/preview", "/x/preview")).with(as(this.owner)))
			.andExpect(status().isBadRequest());
	}

	// AC-VDL-14: 같은 버전 동시 미리보기 요청은 변환 1회
	@Test
	void concurrentPreviewRequestsTriggerOneConversion() throws Exception {
		Long documentId = createDocument(fixture("p.pptx", "sample.pptx"));
		ExecutorService pool = Executors.newFixedThreadPool(5);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				results.add(pool.submit(() -> preview(this.member, documentId, 1).andReturn().getResponse().getStatus()));
			}
			for (Future<Integer> result : results) {
				assertThat(result.get(30, TimeUnit.SECONDS)).isIn(200, 202);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertPdf(awaitPreview(documentId), "DOCFLOW-PREVIEW-PPTX-1", "p.pdf");
		verify(this.converter, times(1)).convertToPdf(any(), any(), any(), any());
	}

	private MockHttpServletResponse awaitPreview(Long documentId) throws Exception {
		return awaitStatus(documentId, 200);
	}

	private MockHttpServletResponse awaitStatus(Long documentId, int expected) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
		while (System.nanoTime() < deadline) {
			MockHttpServletResponse response = preview(this.member, documentId, 1).andReturn().getResponse();
			if (response.getStatus() != 202) {
				assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
				return response;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("preview still pending");
	}

	private static void assertPdf(MockHttpServletResponse response, String marker, String filename) throws IOException {
		assertThat(response.getContentType()).isEqualTo("application/pdf");
		assertThat(response.getHeader("Content-Disposition")).startsWith("attachment; ")
			.contains("filename*=UTF-8''" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
				.replace("+", "%20"));
		byte[] body = response.getContentAsByteArray();
		assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(body.length));
		try (PDDocument document = Loader.loadPDF(body)) {
			assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
			assertThat(new PDFTextStripper().getText(document)).contains(marker);
		}
	}

	private ResultActions preview(User user, Long documentId, int versionNumber) throws Exception {
		return this.mockMvc.perform(get(url(documentId, versionNumber, "preview")).with(as(user)));
	}

	private List<String> originalHashes() throws Exception {
		List<String> hashes = new ArrayList<>();
		for (String key : this.jdbcTemplate.queryForList("SELECT storage_key FROM document_versions ORDER BY id",
				String.class)) {
			hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(Files.readAllBytes(DocumentTestSupport.STORAGE_ROOT.resolve(key)))));
		}
		return hashes;
	}

	private static List<String> filesUnder(String relative) throws IOException {
		Path dir = DocumentTestSupport.STORAGE_ROOT.resolve(relative);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.map((path) -> path.getFileName().toString()).toList();
		}
	}

	private Long createDocument(MockMultipartFile file) throws Exception {
		return createDocument(file, this.projectId, this.owner);
	}

	private Long createDocument(MockMultipartFile file, Long project, User user) throws Exception {
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + project + "/documents").file(file)
				.param("title", "문서")
				.with(as(user))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private Long createProject(User projectOwner) {
		return this.jdbcTemplate.queryForObject("INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id",
				Long.class, projectOwner.getId());
	}

	private String url(Long documentId, int versionNumber, String action) {
		return "/api/projects/" + this.projectId + "/documents/" + documentId + "/versions/" + versionNumber + "/"
				+ action;
	}

	private static MockMultipartFile fixture(String name, String resource) throws IOException {
		return file(name, fixtureBytes(resource));
	}

	static byte[] fixtureBytes(String resource) throws IOException {
		try (InputStream in = DocumentPreviewIntegrationTest.class.getResourceAsStream("/preview/" + resource)) {
			return in.readAllBytes();
		}
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "application/octet-stream", content);
	}

}
