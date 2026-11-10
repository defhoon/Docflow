package com.docflow.document;

import static com.docflow.document.DocumentApiHelper.documentUrl;
import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/** SPEC-07 최종 버전 확정 통합 테스트 (실제 PostgreSQL, 보안 설정 활성). AC-FIN-01~11. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class DocumentFinalizeIntegrationTest {

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

	// AC-FIN-01, 11: 오너가 최신 버전 확정 → FINALIZED, 확정 정보 저장·응답, 활동 이력
	@Test
	void ownerFinalizesLatestVersion() throws Exception {
		Long documentId = documentWithVersions(2);

		finalize(this.owner, documentId, 2).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FINALIZED"))
			.andExpect(jsonPath("$.finalization.versionNumber").value(2))
			.andExpect(jsonPath("$.finalization.finalizedBy.id").value(this.owner.getId()))
			.andExpect(jsonPath("$.finalization.finalizedBy.displayName").value("Owner"))
			.andExpect(jsonPath("$.finalization.finalizedAt").isNotEmpty())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2));

		Map<String, Object> row = documentRow(documentId);
		assertThat(row.get("status")).isEqualTo("FINALIZED");
		assertThat(row.get("final_version_id")).isEqualTo(versionId(documentId, 2));
		assertThat(row.get("finalized_by")).isEqualTo(this.owner.getId());
		assertThat(row.get("finalized_at")).isNotNull();
		Map<String, Object> activity = this.jdbcTemplate
			.queryForMap("SELECT * FROM audit_logs WHERE action = 'DOCUMENT_FINALIZED'");
		assertThat(activity).containsEntry("target_type", "DOCUMENT")
			.containsEntry("target_id", documentId)
			.containsEntry("document_id", documentId)
			.containsEntry("actor_id", this.owner.getId())
			.containsEntry("version_number", 2);

		this.mockMvc.perform(get(documentUrl(this.projectId, documentId)).with(as(this.member)))
			.andExpect(jsonPath("$.status").value("FINALIZED"))
			.andExpect(jsonPath("$.finalization.versionNumber").value(2));
		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/documents").with(as(this.member)))
			.andExpect(jsonPath("$[0].status").value("FINALIZED"))
			.andExpect(jsonPath("$[0].finalVersionNumber").value(2))
			.andExpect(jsonPath("$[0].latestVersionNumber").value(2));
	}

	// AC-FIN-02: 최신이 아닌 과거 버전 확정
	@Test
	void ownerFinalizesOlderVersion() throws Exception {
		Long documentId = documentWithVersions(3);

		finalize(this.owner, documentId, 1).andExpect(status().isOk())
			.andExpect(jsonPath("$.finalization.versionNumber").value(1))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(3));
		assertThat(documentRow(documentId).get("final_version_id")).isEqualTo(versionId(documentId, 1));
		assertThat(versionCount(documentId)).isEqualTo(3);
	}

	// AC-FIN-03: 구성원 403, 비구성원 404, 비로그인 401, CSRF 누락 403 — 모두 변화 없음
	@Test
	void onlyOwnerCanFinalize() throws Exception {
		Long documentId = documentWithVersions(1);

		finalize(this.member, documentId, 1).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		finalize(this.outsider, documentId, 1).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		this.mockMvc.perform(post(documentUrl(this.projectId, documentId) + "/finalize").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"versionNumber\":1}")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(post(documentUrl(this.projectId, documentId) + "/finalize").with(as(this.owner))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"versionNumber\":1}")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertActive(documentId);
	}

	// AC-FIN-04: 다른 문서의 버전·없는 버전·다른 프로젝트 문서 404, 잘못된 입력 400
	@Test
	void rejectsVersionsOfOtherDocumentsAndInvalidInput() throws Exception {
		Long documentId = documentWithVersions(1);
		Long otherDocument = documentWithVersions(3);
		Long foreignProject = this.api.createProject(this.outsider, "P2");
		Long foreignDocument = this.api.createDocument(this.outsider, foreignProject);

		// 다른 문서에는 v3가 있지만 이 문서에는 없다
		finalize(this.owner, documentId, 3).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_FOUND"));
		finalize(this.owner, 999_999L, 1).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		this.mockMvc.perform(post(documentUrl(this.projectId, foreignDocument) + "/finalize").with(as(this.owner))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"versionNumber\":1}")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		for (String body : List.of("{}", "{\"versionNumber\":0}", "{\"versionNumber\":-1}")) {
			finalizeRaw(this.owner, documentId, body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		finalizeRaw(this.owner, documentId, "{\"versionNumber\":\"x\"}").andExpect(status().isBadRequest());

		assertActive(documentId);
		assertActive(otherDocument);
		assertActive(foreignDocument);
	}

	// AC-FIN-05, 09: 재확정(같은·다른 버전) 409, 확정 정보 불변, DB 직접 변경도 트리거가 거부
	@Test
	void finalizedDocumentIsPermanent() throws Exception {
		Long documentId = documentWithVersions(2);
		finalize(this.owner, documentId, 1).andExpect(status().isOk());
		Map<String, Object> before = documentRow(documentId);

		finalize(this.owner, documentId, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_FINALIZED"));
		finalize(this.owner, documentId, 2).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_FINALIZED"));
		finalize(this.member, documentId, 2).andExpect(status().isForbidden());
		assertThat(documentRow(documentId)).isEqualTo(before);
		assertThat(activityCount("DOCUMENT_FINALIZED")).isEqualTo(1);

		assertThatThrownBy(() -> this.jdbcTemplate.update(
				"UPDATE documents SET status = 'ACTIVE', final_version_id = NULL, finalized_by = NULL, finalized_at = NULL WHERE id = ?",
				documentId))
			.hasMessageContaining("is immutable");
		assertThatThrownBy(() -> this.jdbcTemplate.update(
				"UPDATE documents SET final_version_id = ? WHERE id = ?", versionId(documentId, 2), documentId))
			.hasMessageContaining("is immutable");
		assertThat(documentRow(documentId)).isEqualTo(before);
	}

	// AC-FIN-07, 08: 확정 후 새 버전 409(파일·행 없음), 목록·다운로드 가능
	@Test
	void finalizedDocumentIsReadOnlyButReadable() throws Exception {
		Long documentId = documentWithVersions(2);
		finalize(this.owner, documentId, 1).andExpect(status().isOk());
		List<String> files = DocumentTestSupport.storedFiles();

		this.api.uploadVersion(this.owner, this.projectId, documentId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_STATUS_CONFLICT"));
		this.api.uploadVersion(this.member, this.projectId, documentId).andExpect(status().isConflict());
		assertThat(versionCount(documentId)).isEqualTo(2);
		assertThat(DocumentTestSupport.storedFiles()).isEqualTo(files);

		this.mockMvc.perform(get(documentUrl(this.projectId, documentId) + "/versions").with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
		for (int v = 1; v <= 2; v++) {
			this.mockMvc.perform(get(documentUrl(this.projectId, documentId) + "/versions/" + v + "/download")
				.with(as(this.member))).andExpect(status().isOk());
		}
	}

	// AC-FIN-06: 동시 확정 요청 중 정확히 하나만 성공, 활동 이력 1건
	@Test
	void concurrentFinalizeRequestsHaveSingleWinner() throws Exception {
		Long documentId = documentWithVersions(3);
		int n = 6;
		CyclicBarrier barrier = new CyclicBarrier(n);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int versionNumber = (i % 3) + 1;
			tasks.add(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return finalize(this.owner, documentId, versionNumber).andReturn().getResponse().getStatus();
			});
		}
		List<Integer> statuses = runAll(tasks);

		assertThat(statuses).filteredOn((s) -> s == 200).hasSize(1);
		assertThat(statuses).filteredOn((s) -> s == 409).hasSize(n - 1);
		assertThat(activityCount("DOCUMENT_FINALIZED")).isEqualTo(1);
		assertThat(documentRow(documentId).get("status")).isEqualTo("FINALIZED");
	}

	// AC-FIN-10: 확정과 동시에 진행된 업로드는 확정 전에 커밋되었거나 409, 확정 이후 생성된 버전 없음
	@Test
	void concurrentUploadsAndFinalizeStayConsistent() throws Exception {
		Long documentId = documentWithVersions(1);
		int uploads = 5;
		CyclicBarrier barrier = new CyclicBarrier(uploads + 1);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < uploads; i++) {
			User uploader = (i % 2 == 0) ? this.member : this.owner;
			tasks.add(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return this.api.uploadVersion(uploader, this.projectId, documentId).andReturn().getResponse().getStatus();
			});
		}
		tasks.add(() -> {
			barrier.await(10, TimeUnit.SECONDS);
			return finalize(this.owner, documentId, 1).andReturn().getResponse().getStatus();
		});
		List<Integer> statuses = runAll(tasks);

		assertThat(statuses.get(uploads)).isEqualTo(200);
		List<Integer> uploadStatuses = statuses.subList(0, uploads);
		assertThat(uploadStatuses).allMatch((s) -> s == 201 || s == 409);
		long created = uploadStatuses.stream().filter((s) -> s == 201).count();
		assertThat(versionCount(documentId)).isEqualTo(1 + created);
		Long finalizedActivity = this.jdbcTemplate
			.queryForObject("SELECT id FROM audit_logs WHERE action = 'DOCUMENT_FINALIZED'", Long.class);
		assertThat(this.jdbcTemplate.queryForList("SELECT id FROM audit_logs WHERE action = 'VERSION_CREATED'", Long.class))
			.hasSize((int) created)
			.allMatch((id) -> id < finalizedActivity);
		assertThat(DocumentTestSupport.storedFiles()).hasSize((int) (1 + created));
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

	private Long documentWithVersions(int count) throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);
		for (int i = 2; i <= count; i++) {
			this.api.uploadVersion(this.member, this.projectId, documentId).andExpect(status().isCreated());
		}
		return documentId;
	}

	private ResultActions finalize(User user, Long documentId, int versionNumber) throws Exception {
		return finalizeRaw(user, documentId, "{\"versionNumber\":" + versionNumber + "}");
	}

	private ResultActions finalizeRaw(User user, Long documentId, String body) throws Exception {
		return this.mockMvc.perform(post(documentUrl(this.projectId, documentId) + "/finalize").with(as(user))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private void assertActive(Long documentId) {
		Map<String, Object> row = documentRow(documentId);
		assertThat(row.get("status")).isEqualTo("ACTIVE");
		assertThat(row.get("final_version_id")).isNull();
		assertThat(activityCount("DOCUMENT_FINALIZED")).isZero();
	}

	private Map<String, Object> documentRow(Long documentId) {
		return this.jdbcTemplate.queryForMap("SELECT * FROM documents WHERE id = ?", documentId);
	}

	private Long versionId(Long documentId, int versionNumber) {
		return this.jdbcTemplate.queryForObject(
				"SELECT id FROM document_versions WHERE document_id = ? AND version_number = ?", Long.class, documentId,
				versionNumber);
	}

	private long versionCount(Long documentId) {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions WHERE document_id = ?",
				Long.class, documentId);
	}

	private long activityCount(String action) {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ?", Long.class,
				action);
	}

}
