package com.docflow.home;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.docflow.document.DocumentTestFiles;
import com.docflow.document.DocumentTestSupport;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-18 홈 통합 조회 API 통합 테스트 (실제 PostgreSQL, 보안 설정 활성). AC-HOME-01~05.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@DirtiesContext
class HomeApiIntegrationTest {

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

	@BeforeEach
	void setUp() {
		cleanUp();
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.member = this.userAccountService.createUser("member@test.local", PASSWORD, "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", PASSWORD, "Outsider");
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-HOME-01·02: 접근 가능한 프로젝트만 세고, 상태별 문서 수를 정확히 집계한다
	@Test
	void countsOnlyAccessibleProjectsAndDocumentsByStatus() throws Exception {
		Long owned = createProject(this.owner, "내 프로젝트");
		Long joined = createProject(this.outsider, "참여한 프로젝트");
		addMember(joined, this.owner);
		Long foreign = createProject(this.outsider, "남의 프로젝트");
		Long a = createDocument(this.owner, owned, "A", "a.pdf", DocumentTestFiles.pdf());
		createDocument(this.owner, owned, "B", "b.docx", DocumentTestFiles.valid(DocumentFileType.DOCX));
		createDocument(this.outsider, joined, "C", "c.pdf", DocumentTestFiles.pdf());
		createDocument(this.outsider, foreign, "남의 문서", "x.pdf", DocumentTestFiles.pdf());
		DocumentTestSupport.finalizeDirectly(this.jdbcTemplate, a);

		this.mockMvc.perform(get("/api/home").with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.projectCount").value(2))
			.andExpect(jsonPath("$.activeDocumentCount").value(2))
			.andExpect(jsonPath("$.finalizedDocumentCount").value(1))
			.andExpect(jsonPath("$.recentDocuments", hasSize(3)))
			.andExpect(jsonPath("$.recentDocuments[?(@.title == '남의 문서')]", hasSize(0)));

		// 구성원이 아닌 사용자는 아무것도 보지 못한다(빈 홈)
		User stranger = this.userAccountService.createUser("stranger@test.local", PASSWORD, "Stranger");
		this.mockMvc.perform(get("/api/home").with(as(stranger)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.projectCount").value(0))
			.andExpect(jsonPath("$.activeDocumentCount").value(0))
			.andExpect(jsonPath("$.finalizedDocumentCount").value(0))
			.andExpect(jsonPath("$.recentDocuments", hasSize(0)))
			.andExpect(jsonPath("$.recentActivities", hasSize(0)));
	}

	// AC-HOME-03: 최근 문서는 변경 시각 최신순 최대 5개이며, 최신 버전 업로더·형식·프로젝트를 포함한다
	@Test
	void recentDocumentsAreNewestFiveWithLatestVersionInfo() throws Exception {
		Long projectId = createProject(this.owner, "P");
		addMember(projectId, this.member);
		Long[] ids = new Long[6];
		for (int i = 0; i < 6; i++) {
			ids[i] = createDocument(this.owner, projectId, "문서" + i, "d" + i + ".pdf", DocumentTestFiles.pdf());
			this.jdbcTemplate.update("UPDATE documents SET updated_at = ? WHERE id = ?",
					Timestamp.from(Instant.parse("2026-01-0" + (i + 1) + "T00:00:00Z")), ids[i]);
		}
		// 가장 오래된 문서에 구성원이 새 버전을 올리면 맨 앞으로 오고 최신 업로더가 바뀐다
		this.mockMvc
			.perform(multipart("/api/projects/" + projectId + "/documents/" + ids[0] + "/versions")
				.file(file("d0-v2.docx", DocumentTestFiles.valid(DocumentFileType.DOCX)))
				.with(as(this.member))
				.with(csrf()))
			.andExpect(status().isCreated());

		this.mockMvc.perform(get("/api/home").with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recentDocuments", hasSize(5)))
			.andExpect(jsonPath("$.recentDocuments[0].id").value(ids[0]))
			.andExpect(jsonPath("$.recentDocuments[0].latestVersionNumber").value(2))
			.andExpect(jsonPath("$.recentDocuments[0].latestUploader.displayName").value("Member"))
			.andExpect(jsonPath("$.recentDocuments[0].fileType").value("DOCX"))
			.andExpect(jsonPath("$.recentDocuments[0].project.name").value("P"))
			.andExpect(jsonPath("$.recentDocuments[0].finalVersionNumber").doesNotExist())
			.andExpect(jsonPath("$.recentDocuments[1].id").value(ids[5]))
			.andExpect(jsonPath("$.recentDocuments[4].id").value(ids[2]))
			.andExpect(jsonPath("$.recentDocuments[0].*", hasSize(9)))
			.andExpect(jsonPath("$.recentDocuments[0].description").doesNotExist());
	}

	// AC-HOME-04: 최근 활동은 최신순 기본 5개(더 보기는 /api/activities)
	@Test
	void recentActivitiesAreNewestFive() throws Exception {
		Long projectId = createProject(this.owner, "P");
		Long documentId = createDocument(this.owner, projectId, "문서", "a.pdf", DocumentTestFiles.pdf());
		for (int i = 0; i < 6; i++) {
			this.mockMvc
				.perform(post("/api/projects/" + projectId + "/documents/" + documentId + "/versions/1/feedbacks")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"content\":\"의견 " + i + "\"}")
					.with(as(this.owner))
					.with(csrf()))
				.andExpect(status().isCreated());
		}
		Long newest = this.jdbcTemplate.queryForObject("SELECT max(id) FROM audit_logs", Long.class);

		this.mockMvc.perform(get("/api/home").with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recentActivities", hasSize(5)))
			.andExpect(jsonPath("$.recentActivities[0].id").value(newest))
			.andExpect(jsonPath("$.recentActivities[0].action").value("FEEDBACK_CREATED"))
			.andExpect(jsonPath("$.recentActivities[0].document.title").value("문서"))
			.andExpect(jsonPath("$.recentActivities[0].versionNumber").value(1));
	}

	// AC-HOME-05: 프로젝트·문서 수와 무관하게 쿼리 4회(반복 조회 없음), 로그인하지 않으면 401
	@Test
	void usesFixedNumberOfQueriesAndRequiresLogin() throws Exception {
		for (int p = 0; p < 3; p++) {
			Long projectId = createProject(this.owner, "P" + p);
			for (int d = 0; d < 3; d++) {
				createDocument(this.owner, projectId, "문서" + d, "d.pdf", DocumentTestFiles.pdf());
			}
		}
		Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		this.mockMvc.perform(get("/api/home").with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.projectCount").value(3))
			.andExpect(jsonPath("$.activeDocumentCount").value(9));
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);

		this.mockMvc.perform(get("/api/home")).andExpect(status().isUnauthorized());
	}

	private Long createProject(User projectOwner, String name) {
		return this.jdbcTemplate.queryForObject("INSERT INTO projects (name, owner_id) VALUES (?, ?) RETURNING id",
				Long.class, name, projectOwner.getId());
	}

	private void addMember(Long projectId, User user) {
		this.jdbcTemplate.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", projectId,
				user.getId());
	}

	private Long createDocument(User user, Long projectId, String title, String filename, byte[] content)
			throws Exception {
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + projectId + "/documents").file(file(filename, content))
				.param("title", title)
				.with(as(user))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "application/octet-stream", content);
	}

}
