package com.docflow.security;

import static com.docflow.document.DocumentApiHelper.documentUrl;
import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.docflow.document.DocumentApiHelper;
import com.docflow.document.DocumentTestSupport;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * #37 공개 전 권한·불변성 회귀: 문서·버전·파일·피드백·활동·확정 API 전체에 대해 비로그인 401, 비구성원 404, 구성원 확정 403,
 * FINALIZED 이후 모든 변경 거부, 수정·삭제 API 부재, 파일 경로 조작·저장소 경로 비노출을 한 곳에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class AccessControlRegressionIntegrationTest {

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

	private Long documentId;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		this.api = new DocumentApiHelper(this.mockMvc, this.jdbcTemplate);
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.member = this.userAccountService.createUser("member@test.local", PASSWORD, "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", PASSWORD, "Outsider");
		this.projectId = this.api.createProject(this.owner, "P1");
		this.api.addMember(this.projectId, this.member);
		this.documentId = this.api.createDocument(this.member, this.projectId);
		this.mockMvc.perform(post(documentUrl(this.projectId, this.documentId) + "/versions/1/feedbacks").with(as(this.member))
			.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"의견\"}"))
			.andExpect(status().isCreated());
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	private List<String> readUrls() {
		String doc = documentUrl(this.projectId, this.documentId);
		return List.of("/api/projects/" + this.projectId + "/documents", doc, doc + "/versions", doc + "/versions/1/download",
				doc + "/versions/1/preview", doc + "/versions/1/feedbacks", doc + "/activities",
				"/api/projects/" + this.projectId + "/activities");
	}

	/** 요청마다 새 빌더를 만든다(빌더의 with()는 같은 인스턴스에 누적된다). */
	private List<Supplier<AbstractMockHttpServletRequestBuilder<?>>> writes() {
		String doc = documentUrl(this.projectId, this.documentId);
		return List.of(
				() -> multipart("/api/projects/" + this.projectId + "/documents").file(DocumentApiHelper.pdf()).param("title", "t"),
				() -> multipart(doc + "/versions").file(DocumentApiHelper.pdf()),
				() -> post(doc + "/versions/1/feedbacks").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"),
				() -> post(doc + "/finalize").contentType(MediaType.APPLICATION_JSON).content("{\"versionNumber\":1}"));
	}

	@Test
	void unauthenticatedRequestsGet401() throws Exception {
		for (String url : readUrls()) {
			this.mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
		}
		for (Supplier<AbstractMockHttpServletRequestBuilder<?>> write : writes()) {
			this.mockMvc.perform(write.get().with(csrf())).andExpect(status().isUnauthorized());
		}
		assertUnchanged();
	}

	@Test
	void outsidersGet404ForEveryDocumentResource() throws Exception {
		for (String url : readUrls()) {
			this.mockMvc.perform(get(url).with(as(this.outsider))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		}
		for (Supplier<AbstractMockHttpServletRequestBuilder<?>> write : writes()) {
			this.mockMvc.perform(write.get().with(as(this.outsider)).with(csrf())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		}
		assertUnchanged();
	}

	@Test
	void membersCannotFinalize() throws Exception {
		this.mockMvc.perform(post(documentUrl(this.projectId, this.documentId) + "/finalize").with(as(this.member))
			.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"versionNumber\":1}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		assertUnchanged();
	}

	@Test
	void finalizedDocumentRejectsEveryChangeButStaysReadable() throws Exception {
		this.mockMvc.perform(post(documentUrl(this.projectId, this.documentId) + "/finalize").with(as(this.owner))
			.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"versionNumber\":1}"))
			.andExpect(status().isOk());
		Map<String, Object> before = snapshot();

		var changes = writes().subList(1, 4); // 새 버전, 피드백, 재확정
		for (User user : List.of(this.owner, this.member)) {
			for (Supplier<AbstractMockHttpServletRequestBuilder<?>> change : changes) {
				int status = this.mockMvc.perform(change.get().with(as(user)).with(csrf())).andReturn().getResponse().getStatus();
				// 오너: 409(상태). 구성원의 재확정은 권한 검사가 먼저라 403이며, 어느 쪽도 변경되지 않는다.
				assertThat(status).isIn(409, 403);
			}
		}
		for (String url : readUrls()) {
			if (!url.endsWith("/preview")) {
				this.mockMvc.perform(get(url).with(as(this.member))).andExpect(status().isOk());
			}
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	// 상태 변경·수정·삭제 API는 존재하지 않는다(FINALIZED → ACTIVE 경로 없음)
	@Test
	void noUpdateOrDeleteEndpointsExist() throws Exception {
		String doc = documentUrl(this.projectId, this.documentId);
		List<RequestBuilder> attempts = List.of(put(doc).with(as(this.owner)).with(csrf()),
				patch(doc).with(as(this.owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"),
				delete(doc).with(as(this.owner)).with(csrf()), delete(doc + "/versions/1").with(as(this.owner)).with(csrf()),
				delete(doc + "/versions/1/feedbacks/1").with(as(this.owner)).with(csrf()),
				post(doc + "/reopen").with(as(this.owner)).with(csrf()), post(doc + "/status").with(as(this.owner)).with(csrf()));
		for (RequestBuilder attempt : attempts) {
			int status = this.mockMvc.perform(attempt).andReturn().getResponse().getStatus();
			assertThat(status).isIn(404, 405);
		}
		assertUnchanged();
	}

	// 파일은 저장 키가 아니라 (프로젝트, 문서, 버전 번호)로만 접근하며, 저장소 경로는 HTTP로 노출되지 않는다
	@Test
	void storagePathsAreNeverServed() throws Exception {
		String key = this.jdbcTemplate.queryForObject("SELECT storage_key FROM document_versions LIMIT 1", String.class);
		String doc = documentUrl(this.projectId, this.documentId);
		for (String url : List.of("/" + key, "/storage/" + key, "/data/storage/" + key, "/api/" + key,
				doc + "/versions/..%2F..%2F" + key.replace("/", "%2F") + "/download",
				doc + "/versions/" + key.replace("/", "%2F") + "/download",
				doc + "/versions/1/download/../../../../" + key)) {
			int status = this.mockMvc.perform(get(url).with(as(this.owner))).andReturn().getResponse().getStatus();
			assertThat(status).as(url).isNotEqualTo(200).isBetween(400, 499);
		}
	}

	private Map<String, Object> snapshot() {
		return Map.of("documents", this.jdbcTemplate.queryForList("SELECT * FROM documents ORDER BY id"), "versions",
				this.jdbcTemplate.queryForList("SELECT id FROM document_versions ORDER BY id"), "feedbacks",
				this.jdbcTemplate.queryForList("SELECT id FROM feedbacks ORDER BY id"), "files", DocumentTestSupport.storedFiles());
	}

	private void assertUnchanged() {
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Long.class)).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions", Long.class)).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM feedbacks", Long.class)).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT status FROM documents", String.class)).isEqualTo("ACTIVE");
		assertThat(DocumentTestSupport.storedFiles()).hasSize(1);
	}

}
