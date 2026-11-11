package com.docflow.feedback;

import static com.docflow.document.DocumentApiHelper.documentUrl;
import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.document.DocumentApiHelper;
import com.docflow.document.DocumentTestSupport;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

import tools.jackson.databind.json.JsonMapper;

/** SPEC-08 버전별 피드백 통합 테스트 (실제 PostgreSQL, 보안 설정 활성). AC-FB-01~09. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class FeedbackIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

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

	private DocumentApiHelper api;

	private User owner;

	private User member;

	private User outsider;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.api = new DocumentApiHelper(this.mockMvc, this.jdbcTemplate);
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.member = this.userAccountService.createUser("member@test.local", PASSWORD, "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", PASSWORD, "Outsider");
		this.projectId = this.api.createProject(this.owner, "P1");
		this.api.addMember(this.projectId, this.member);
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-FB-01, 02, 03, 07: 오너·구성원 작성, 버전별 독립 목록(작성 순), 새 버전 후 유지, 활동 이력
	@Test
	void membersWriteIndependentFeedbackPerVersion() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);

		write(this.member, documentId, 1, "  v1 표 2의 수치를 확인해 주세요.\n두 번째 줄  ").andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/versions/1/feedbacks/")))
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.author.id").value(this.member.getId()))
			.andExpect(jsonPath("$.author.displayName").value("Member"))
			.andExpect(jsonPath("$.content").value("v1 표 2의 수치를 확인해 주세요.\n두 번째 줄"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.*", hasSize(5)));
		write(this.owner, documentId, 1, "오너 의견").andExpect(status().isCreated());

		this.api.uploadVersion(this.member, this.projectId, documentId).andExpect(status().isCreated());
		write(this.owner, documentId, 2, "v2 의견").andExpect(status().isCreated());

		list(this.member, documentId, 1).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].author.displayName").value("Member"))
			.andExpect(jsonPath("$[1].content").value("오너 의견"));
		list(this.owner, documentId, 2).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].content").value("v2 의견"))
			.andExpect(jsonPath("$[0].versionNumber").value(2));

		List<Map<String, Object>> activities = this.jdbcTemplate
			.queryForList("SELECT * FROM audit_logs WHERE action = 'FEEDBACK_CREATED' ORDER BY id");
		assertThat(activities).hasSize(3);
		Long lastFeedback = this.jdbcTemplate.queryForObject("SELECT max(id) FROM feedbacks", Long.class);
		assertThat(activities.get(2)).containsEntry("target_type", "FEEDBACK")
			.containsEntry("target_id", lastFeedback)
			.containsEntry("document_id", documentId)
			.containsEntry("version_number", 2)
			.containsEntry("actor_id", this.owner.getId());
	}

	// AC-FB-04: 비구성원 404, 비로그인 401, CSRF 누락 403, 변화 없음
	@Test
	void outsidersAndUnauthenticatedRequestsAreRejected() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);

		write(this.outsider, documentId, 1, "x").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		list(this.outsider, documentId, 1).andExpect(status().isNotFound());
		this.mockMvc.perform(post(feedbackUrl(documentId, 1)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"x\"}")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get(feedbackUrl(documentId, 1))).andExpect(status().isUnauthorized());
		this.mockMvc.perform(post(feedbackUrl(documentId, 1)).with(as(this.member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"x\"}")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertThat(feedbackCount()).isZero();
	}

	// AC-FB-05: 잘못된 내용 400, 다른 문서의 버전·없는 문서·다른 프로젝트 문서 404
	@Test
	void rejectsInvalidContentAndForeignTargets() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);
		Long other = this.api.createDocument(this.owner, this.projectId);
		this.api.uploadVersion(this.owner, this.projectId, other).andExpect(status().isCreated());
		Long foreignProject = this.api.createProject(this.outsider, "P2");
		Long foreignDocument = this.api.createDocument(this.outsider, foreignProject);

		for (String body : List.of("{}", "{\"content\":\"\"}", "{\"content\":\"   \\n  \"}",
				"{\"content\":\"" + "가".repeat(2001) + "\"}")) {
			writeRaw(this.member, documentId, 1, body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		write(this.member, documentId, 1, "가".repeat(2000)).andExpect(status().isCreated());
		write(this.member, documentId, 1, "😀".repeat(2000)).andExpect(status().isCreated());

		write(this.member, documentId, 2, "x").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_FOUND"));
		list(this.member, documentId, 2).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_FOUND"));
		write(this.member, 999_999L, 1, "x").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		this.mockMvc.perform(post("/api/projects/" + this.projectId + "/documents/" + foreignDocument
				+ "/versions/1/feedbacks").with(as(this.owner)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"x\"}")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

		assertThat(feedbackCount()).isEqualTo(2);
	}

	// AC-FB-06: 확정 이후 작성 409, 기존 피드백 조회 가능
	@Test
	void finalizedDocumentKeepsFeedbackReadOnly() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);
		write(this.member, documentId, 1, "확정 전 의견").andExpect(status().isCreated());
		finalize(documentId, 1).andExpect(status().isOk());

		write(this.member, documentId, 1, "확정 후 의견").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_FINALIZED"));
		write(this.owner, documentId, 1, "확정 후 오너 의견").andExpect(status().isConflict());

		list(this.member, documentId, 1).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].content").value("확정 전 의견"));
		assertThat(feedbackCount()).isEqualTo(1);
	}

	// AC-FB-08: 확정과 동시에 진행된 피드백은 확정 전에 커밋되었거나 409
	@Test
	void concurrentFeedbackAndFinalizeStayConsistent() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);
		int writers = 8;
		CyclicBarrier barrier = new CyclicBarrier(writers + 1);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < writers; i++) {
			User writer = (i % 2 == 0) ? this.member : this.owner;
			String content = "의견 " + i;
			tasks.add(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return write(writer, documentId, 1, content).andReturn().getResponse().getStatus();
			});
		}
		tasks.add(() -> {
			barrier.await(10, TimeUnit.SECONDS);
			return finalize(documentId, 1).andReturn().getResponse().getStatus();
		});
		List<Integer> statuses = runAll(tasks);

		assertThat(statuses.get(writers)).isEqualTo(200);
		List<Integer> writes = statuses.subList(0, writers);
		assertThat(writes).allMatch((s) -> s == 201 || s == 409);
		long created = writes.stream().filter((s) -> s == 201).count();
		assertThat(feedbackCount()).isEqualTo(created);
		Long finalizedActivity = this.jdbcTemplate
			.queryForObject("SELECT id FROM audit_logs WHERE action = 'DOCUMENT_FINALIZED'", Long.class);
		assertThat(this.jdbcTemplate.queryForList("SELECT id FROM audit_logs WHERE action = 'FEEDBACK_CREATED'", Long.class))
			.hasSize((int) created)
			.allMatch((id) -> id < finalizedActivity);
	}

	// AC-FB-09: HTML·스크립트 문자열은 변환 없이 저장·반환된다
	@Test
	void markupIsStoredVerbatim() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);
		String markup = "<script>alert('x')</script> & <b>bold</b>";

		String body = write(this.member, documentId, 1, markup).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		assertThat(this.jsonMapper.readTree(body).get("content").asString()).isEqualTo(markup);
		assertThat(this.jdbcTemplate.queryForObject("SELECT content FROM feedbacks", String.class)).isEqualTo(markup);
	}

	private List<Integer> runAll(List<Callable<Integer>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> task : tasks) {
				futures.add(executor.submit(task));
			}
			List<Integer> results = new ArrayList<>();
			for (Future<Integer> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private ResultActions write(User user, Long documentId, int versionNumber, String content) throws Exception {
		return writeRaw(user, documentId, versionNumber,
				this.jsonMapper.writeValueAsString(Map.of("content", content)));
	}

	private ResultActions writeRaw(User user, Long documentId, int versionNumber, String body) throws Exception {
		return this.mockMvc.perform(post(feedbackUrl(documentId, versionNumber)).with(as(user))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions list(User user, Long documentId, int versionNumber) throws Exception {
		return this.mockMvc.perform(get(feedbackUrl(documentId, versionNumber)).with(as(user)));
	}

	private ResultActions finalize(Long documentId, int versionNumber) throws Exception {
		return this.mockMvc.perform(post(documentUrl(this.projectId, documentId) + "/finalize").with(as(this.owner))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"versionNumber\":" + versionNumber + "}"));
	}

	private String feedbackUrl(Long documentId, int versionNumber) {
		return documentUrl(this.projectId, documentId) + "/versions/" + versionNumber + "/feedbacks";
	}

	private long feedbackCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM feedbacks", Long.class);
	}

}
