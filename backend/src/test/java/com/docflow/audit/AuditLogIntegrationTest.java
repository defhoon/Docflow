package com.docflow.audit;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;

import com.docflow.audit.domain.AuditAction;
import com.docflow.audit.domain.AuditLog;
import com.docflow.audit.domain.AuditTargetType;
import com.docflow.audit.repository.AuditLogRepository;
import com.docflow.audit.service.AuditLogService;
import com.docflow.document.DocumentApiHelper;
import com.docflow.document.DocumentTestSupport;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/** SPEC-06 활동 이력 통합 테스트 (실제 PostgreSQL, 보안 설정 활성). AC-AUD-01~09. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class AuditLogIntegrationTest {

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
	private AuditLogService auditLogService;

	@MockitoSpyBean
	private AuditLogRepository auditLogRepository;

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

	// AC-AUD-01, 02: 문서 등록·새 버전 등록이 같은 트랜잭션에서 기록된다
	@Test
	void recordsDocumentAndVersionCreation() throws Exception {
		Long documentId = this.api.createDocument(this.member, this.projectId);
		this.api.uploadVersion(this.owner, this.projectId, documentId).andExpect(status().isCreated());

		List<Map<String, Object>> rows = this.jdbcTemplate
			.queryForList("SELECT * FROM audit_logs ORDER BY id");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).containsEntry("action", "DOCUMENT_CREATED")
			.containsEntry("target_type", "DOCUMENT")
			.containsEntry("target_id", documentId)
			.containsEntry("document_id", documentId)
			.containsEntry("project_id", this.projectId)
			.containsEntry("actor_id", this.member.getId())
			.containsEntry("version_number", 1);
		Long v2Id = this.jdbcTemplate.queryForObject(
				"SELECT id FROM document_versions WHERE document_id = ? AND version_number = 2", Long.class, documentId);
		assertThat(rows.get(1)).containsEntry("action", "VERSION_CREATED")
			.containsEntry("target_type", "DOCUMENT_VERSION")
			.containsEntry("target_id", v2Id)
			.containsEntry("actor_id", this.owner.getId())
			.containsEntry("version_number", 2);
	}

	// AC-AUD-03: 업무가 거부되면 이력이 남지 않는다
	@Test
	void rejectedBusinessRequestLeavesNoActivity() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);
		this.api.uploadVersion(this.outsider, this.projectId, documentId).andExpect(status().isNotFound());
		this.api.createDocumentRequest(this.outsider, this.projectId).andExpect(status().isNotFound());

		assertThat(this.jdbcTemplate.queryForList("SELECT action FROM audit_logs", String.class))
			.containsExactly("DOCUMENT_CREATED");
	}

	// AC-AUD-04: 이력 저장이 실패하면 문서·버전·파일이 남지 않는다
	@Test
	void activityFailureRollsBackBusinessChange() throws Exception {
		willThrow(new IllegalStateException("audit down")).given(this.auditLogRepository).save(any(AuditLog.class));

		this.api.createDocumentRequest(this.owner, this.projectId).andExpect(status().isInternalServerError());

		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Long.class)).isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions", Long.class)).isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isZero();
		assertThat(DocumentTestSupport.storedFiles()).isEmpty();
	}

	// AC-AUD-05, 08: 구성원·오너 조회(최신순, 내부 정보 없음), 비구성원 404, 비로그인 401
	@Test
	void membersReadProjectAndDocumentActivity() throws Exception {
		Long doc1 = this.api.createDocument(this.owner, this.projectId);
		Long doc2 = this.api.createDocument(this.member, this.projectId);
		this.api.uploadVersion(this.member, this.projectId, doc1).andExpect(status().isCreated());

		String body = this.mockMvc.perform(get("/api/projects/" + this.projectId + "/activities").with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].action").value("VERSION_CREATED"))
			.andExpect(jsonPath("$[0].actor.displayName").value("Member"))
			.andExpect(jsonPath("$[0].project.name").value("P1"))
			.andExpect(jsonPath("$[0].document.id").value(doc1))
			.andExpect(jsonPath("$[0].document.title").value("문서"))
			.andExpect(jsonPath("$[0].versionNumber").value(2))
			.andExpect(jsonPath("$[1].document.id").value(doc2))
			.andExpect(jsonPath("$[2].action").value("DOCUMENT_CREATED"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("storage", "@test.local", "documents/");

		this.mockMvc.perform(get(DocumentApiHelper.documentUrl(this.projectId, doc1) + "/activities").with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].versionNumber").value(2))
			.andExpect(jsonPath("$[1].versionNumber").value(1));

		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/activities").with(as(this.outsider)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		this.mockMvc.perform(get(DocumentApiHelper.documentUrl(this.projectId, doc1) + "/activities").with(as(this.outsider)))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/activities")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/activities")).andExpect(status().isUnauthorized());

		Long foreignProject = this.api.createProject(this.outsider, "P2");
		Long foreignDoc = this.api.createDocument(this.outsider, foreignProject);
		this.mockMvc.perform(get(DocumentApiHelper.documentUrl(this.projectId, foreignDoc) + "/activities").with(as(this.owner)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
	}

	// AC-AUD-06: 내 최근 활동은 접근 가능한 프로젝트 이력만
	@Test
	void myActivitiesIncludeOnlyAccessibleProjects() throws Exception {
		this.api.createDocument(this.member, this.projectId);
		Long foreignProject = this.api.createProject(this.outsider, "P2");
		this.api.createDocument(this.outsider, foreignProject);
		Long ownProject = this.api.createProject(this.member, "P3");
		this.api.createDocument(this.member, ownProject);

		this.mockMvc.perform(get("/api/activities").with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].project.name").value("P3"))
			.andExpect(jsonPath("$[1].project.name").value("P1"));
		this.mockMvc.perform(get("/api/activities").with(as(this.outsider)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].project.name").value("P2"));
	}

	// AC-AUD-07: 커서 페이지가 겹치거나 빠지지 않고, 잘못된 값은 400
	@Test
	void pagesWithCursorAndRejectsInvalidParameters() throws Exception {
		Long documentId = this.api.createDocument(this.owner, this.projectId);
		for (int i = 0; i < 4; i++) {
			this.api.uploadVersion(this.owner, this.projectId, documentId).andExpect(status().isCreated());
		}
		List<Long> ids = this.jdbcTemplate.queryForList("SELECT id FROM audit_logs ORDER BY id DESC", Long.class);
		String url = "/api/projects/" + this.projectId + "/activities";

		this.mockMvc.perform(get(url).param("limit", "2").with(as(this.owner)))
			.andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(ids.get(0).intValue(), ids.get(1).intValue())));
		this.mockMvc.perform(get(url).param("limit", "2").param("beforeId", ids.get(1).toString()).with(as(this.owner)))
			.andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(ids.get(2).intValue(), ids.get(3).intValue())));
		this.mockMvc.perform(get(url).param("limit", "2").param("beforeId", ids.get(3).toString()).with(as(this.owner)))
			.andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(ids.get(4).intValue())));

		for (String[] bad : List.of(new String[] { "limit", "0" }, new String[] { "limit", "101" },
				new String[] { "beforeId", "0" })) {
			this.mockMvc.perform(get(url).param(bad[0], bad[1]).with(as(this.owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		this.mockMvc.perform(get(url).param("limit", "abc").with(as(this.owner))).andExpect(status().isBadRequest());
	}

	// AC-AUD-09: 트랜잭션 밖에서는 기록할 수 없다
	@Test
	void recordRequiresExistingTransaction() {
		assertThatThrownBy(() -> this.auditLogService.record(this.projectId, this.owner.getId(),
				AuditAction.DOCUMENT_CREATED, AuditTargetType.DOCUMENT, 1L, null, null))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isZero();
	}

}
