package com.docflow.analysis;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.analysis.service.AnalysisWorker;
import com.docflow.document.DocumentApiHelper;
import com.docflow.document.DocumentTestFiles;
import com.docflow.document.DocumentTestSupport;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.extraction.TestPdfs;
import com.docflow.extraction.TestPdfs.TestPage;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/** SPEC-14 AI 변경 요약 통합 테스트 (실제 PostgreSQL, 가짜 LLM). AC-CS-01~08. */
@SpringBootTest(properties = { "docflow.ai.enabled=true", "docflow.ai.api-key=test-key-not-used",
		"docflow.ai.worker.initial-delay-ms=3600000" })
@AutoConfigureMockMvc
@DirtiesContext
class ChangeSummaryIntegrationTest {

	@TestConfiguration
	static class FakeLlmConfig {

		@Bean
		@Primary
		FakeLlmClient fakeLlmClient() {
			return new FakeLlmClient();
		}

	}

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
	private AnalysisWorker worker;

	@Autowired
	private FakeLlmClient llm;

	private User owner;

	private User member;

	private User outsider;

	private Long projectId;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		this.owner = this.userAccountService.createUser("owner@test.local", "Correct-Horse-Battery-9", "Owner");
		this.member = this.userAccountService.createUser("member@test.local", "Correct-Horse-Battery-9", "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", "Correct-Horse-Battery-9", "Out");
		DocumentApiHelper api = new DocumentApiHelper(this.mockMvc, this.jdbcTemplate);
		this.projectId = api.createProject(this.owner, "P1");
		api.addMember(this.projectId, this.member);
		this.llm.reset();
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	private void enableAi() throws Exception {
		this.mockMvc
			.perform(put("/api/projects/" + this.projectId + "/ai-settings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":true,\"consent\":true}")
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isOk());
	}

	private Long document(byte[]... versions) throws Exception {
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents")
				.file(new MockMultipartFile("file", "v1.pdf", "application/octet-stream", versions[0]))
				.param("title", "문서")
				.with(as(this.member))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		Long documentId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		for (int i = 1; i < versions.length; i++) {
			String name = (versions[i][0] == '%') ? "v.pdf" : "v.docx";
			this.mockMvc
				.perform(multipart(DocumentApiHelper.documentUrl(this.projectId, documentId) + "/versions")
					.file(new MockMultipartFile("file", name, "application/octet-stream", versions[i]))
					.with(as(this.member))
					.with(csrf()))
				.andExpect(status().isCreated());
		}
		return documentId;
	}

	private String url(Long documentId, int base, int target) {
		return DocumentApiHelper.documentUrl(this.projectId, documentId) + "/compare/summary?base=" + base + "&target="
				+ target;
	}

	private ResultActions request(User user, Long documentId, int base, int target) throws Exception {
		return this.mockMvc.perform(post(url(documentId, base, target)).with(as(user)).with(csrf()));
	}

	private ResultActions read(User user, Long documentId, int base, int target) throws Exception {
		return this.mockMvc.perform(get(url(documentId, base, target)).with(as(user)));
	}

	private static byte[] v1() {
		return TestPdfs.pages(TestPage.a4("사업 계획서 제목", "", "예산은 1,200만원 입니다", "", "출시일은 12월입니다"));
	}

	private static byte[] v2() {
		return TestPdfs.pages(TestPage.a4("사업 계획서 제목", "", "예산은 1,500만원 입니다", "", "출시일은 12월입니다", "",
				"보안 점검을 추가합니다"));
	}

	// AC-CS-01~03: Diff 변경만 입력으로 보내고, 근거 변경 ID를 검증해 저장한다
	@Test
	void summarizesDiffWithVerifiedChangeIds() throws Exception {
		Long documentId = document(v1(), v2());
		// AI를 켜기 전에 올린 버전이므로 자동 요약(SPEC-11) 작업은 생기지 않는다.
		enableAi();
		this.llm.respondJson((request) -> """
				{"overview":"예산이 늘고 보안 점검이 추가되었습니다.",
				 "added":[{"text":"보안 점검 추가","changeIds":[1]},{"text":"없는 변경","changeIds":[99]}],
				 "deleted":[],
				 "modified":[{"text":"예산 증액","changeIds":[0]},{"text":"유형 불일치","changeIds":[1]}],
				 "keyChanges":[{"type":"NUMBER","label":"예산","before":"1,200만원","after":"1,500만원","changeIds":[0]},
				               {"type":"DATE","label":"출시일","before":"12월","after":"1월","changeIds":[0]}],
				 "significance":{"level":"MEDIUM","reason":"예산 변경"}}""");
		request(this.member, documentId, 1, 2).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("PENDING"));
		this.worker.runOnce();

		String user = this.llm.requests.getFirst().user();
		assertThat(user).startsWith("<changes>").contains("#0 MODIFIED p.1", "before: 1,200만원", "after: 1,500만원",
				"#1 ADDED p.1", "after: 보안 점검을 추가합니다");
		assertThat(user).doesNotContain("사업 계획서 제목").doesNotContain("출시일은");
		assertThat(this.llm.requests.getFirst().system()).contains("목록에 없는 변경을 만들거나 추측하지 마세요");

		read(this.member, documentId, 1, 2).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.result.overview").value("예산이 늘고 보안 점검이 추가되었습니다."))
			.andExpect(jsonPath("$.result.added.length()").value(1))
			.andExpect(jsonPath("$.result.added[0].changeIds[0]").value(1))
			.andExpect(jsonPath("$.result.modified.length()").value(1))
			.andExpect(jsonPath("$.result.keyChanges.length()").value(1))
			.andExpect(jsonPath("$.result.keyChanges[0].after").value("1,500만원"))
			.andExpect(jsonPath("$.result.significance.level").value("MEDIUM"))
			.andExpect(jsonPath("$.result.meta.changeCount").value(2))
			.andExpect(jsonPath("$.result.meta.droppedItems").value(3));
		assertThat(this.jdbcTemplate.queryForMap("SELECT analysis_type, version_id, base_version_id FROM document_analyses"))
			.containsEntry("analysis_type", "CHANGE_SUMMARY");
	}

	// AC-CS-04: 변경이 없으면 API를 호출하지 않는다
	@Test
	void identicalVersionsDoNotCallApi() throws Exception {
		Long documentId = document(v1(), v1());
		// AI를 켜기 전에 올린 버전이므로 자동 요약(SPEC-11) 작업은 생기지 않는다.
		enableAi();
		request(this.member, documentId, 1, 2).andExpect(status().isAccepted());
		this.worker.runOnce();
		assertThat(this.llm.requests).isEmpty();
		read(this.member, documentId, 1, 2).andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.result.significance.level").value("NONE"))
			.andExpect(jsonPath("$.result.meta.changeCount").value(0));
	}

	// AC-CS-05: 같은 조합은 한 번만 만들고 호출하며, 조합이 다르면 따로 저장한다
	@Test
	void oneAnalysisPerVersionPair() throws Exception {
		Long documentId = document(v1(), v2());
		// AI를 켜기 전에 올린 버전이므로 자동 요약(SPEC-11) 작업은 생기지 않는다.
		enableAi();
		this.llm.respondJson((r) -> "{\"overview\":\"요약\",\"added\":[],\"deleted\":[],\"modified\":[],"
				+ "\"keyChanges\":[],\"significance\":{\"level\":\"LOW\",\"reason\":\"r\"}}");
		for (int i = 0; i < 3; i++) {
			request(this.member, documentId, 1, 2).andExpect(status().isAccepted());
		}
		this.worker.runOnce();
		request(this.member, documentId, 1, 2).andExpect(jsonPath("$.status").value("COMPLETED"));
		this.worker.runOnce();
		assertThat(this.llm.requests).hasSize(1);
		request(this.member, documentId, 2, 1).andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_analyses", Integer.class))
			.isEqualTo(2);
		read(this.member, documentId, 1, 2).andExpect(jsonPath("$.result.overview").value("요약"));
	}

	// AC-CS-06: 권한·AI 꺼짐·잘못된 조합
	@Test
	void accessAndPreconditions() throws Exception {
		Long documentId = document(v1(), v2(), DocumentTestFiles.valid(DocumentFileType.DOCX));
		request(this.member, documentId, 1, 2).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AI_DISABLED_FOR_PROJECT"));
		enableAi();
		request(this.outsider, documentId, 1, 2).andExpect(status().isNotFound());
		read(this.outsider, documentId, 1, 2).andExpect(status().isNotFound());
		request(this.member, documentId, 1, 1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMPARE_SAME_VERSION"));
		request(this.member, documentId, 1, 9).andExpect(status().isNotFound());
		request(this.member, documentId, 1, 3).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ANALYSIS_UNSUPPORTED_TYPE"));
		read(this.member, documentId, 1, 2).andExpect(jsonPath("$.status").value("NONE"))
			.andExpect(jsonPath("$.aiEnabled").value(true));
	}

	// AC-CS-07: 비교할 텍스트가 없으면 호출 없이 실패하고 다시 요청할 수 없다
	@Test
	void textUnavailableFailsWithoutCall() throws Exception {
		Long documentId = document(v1(), TestPdfs.noText());
		// AI를 켜기 전에 올린 버전이므로 자동 요약(SPEC-11) 작업은 생기지 않는다.
		enableAi();
		request(this.member, documentId, 1, 2).andExpect(status().isAccepted());
		this.worker.runOnce();
		assertThat(this.llm.requests).isEmpty();
		read(this.member, documentId, 1, 2).andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.errorCode").value("TEXT_NO_TEXT"))
			.andExpect(jsonPath("$.retryable").value(false));
	}

	// AC-CS-08: 근거가 하나도 확인되지 않는 응답은 항목 없이 개요만 남긴다(없는 변경 생성 방지)
	@Test
	void ungroundedItemsAreDropped() throws Exception {
		Long documentId = document(v1(), v2());
		// AI를 켜기 전에 올린 버전이므로 자동 요약(SPEC-11) 작업은 생기지 않는다.
		enableAi();
		this.llm.respondJson((request) -> """
				{"overview":"변경 요약","added":[{"text":"가짜","changeIds":[]}],"deleted":[{"text":"가짜 삭제","changeIds":[5]}],
				 "modified":[],"keyChanges":[{"type":"NUMBER","label":"x","before":"999","after":"1000","changeIds":[0]}],
				 "significance":{"level":"HIGH","reason":"r"}}""");
		request(this.member, documentId, 1, 2);
		this.worker.runOnce();
		read(this.member, documentId, 1, 2).andExpect(jsonPath("$.result.added.length()").value(0))
			.andExpect(jsonPath("$.result.deleted.length()").value(0))
			.andExpect(jsonPath("$.result.keyChanges.length()").value(0))
			.andExpect(jsonPath("$.result.meta.droppedItems").value(3));
	}

}
