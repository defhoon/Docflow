package com.docflow.project;

import static com.docflow.project.ProjectApiIntegrationTest.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-02 구성원 API 통합 테스트 (실제 PostgreSQL, 보안 설정 활성 상태).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectMemberApiIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JsonMapper jsonMapper;

	private User author;

	private User reviewer;

	private User outsider;

	private long projectId;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		this.author = this.userAccountService.createUser("author@test.local", PASSWORD, "Author");
		this.reviewer = this.userAccountService.createUser("reviewer@test.local", PASSWORD, "Reviewer");
		this.outsider = this.userAccountService.createUser("outsider@test.local", PASSWORD, "Outsider");
		this.projectId = createProject(this.author, "Author 프로젝트");
	}

	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM projects");
		this.jdbcTemplate.update("DELETE FROM users");
	}

	// AC-MEMBER-01, 04: 추가 201, 이메일 정규화, 응답에 email·Location 없음
	@Test
	void ownerAddsRegisteredUserByEmail() throws Exception {
		add(this.author, this.projectId, "  Reviewer@TEST.local ").andExpect(status().isCreated())
			.andExpect(header().doesNotExist("Location"))
			.andExpect(jsonPath("$.userId").value(this.reviewer.getId()))
			.andExpect(jsonPath("$.displayName").value("Reviewer"))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.addedAt").isString())
			.andExpect(jsonPath("$.email").doesNotExist())
			.andExpect(jsonPath("$.*", hasSize(4)));

		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT user_id FROM project_members WHERE project_id = ?", Long.class, this.projectId))
			.isEqualTo(this.reviewer.getId());
	}

	// AC-MEMBER-02, 03, 04: MEMBER의 목록·상세·구성원 조회, myRole, 이메일 미포함
	@Test
	void memberCanViewProjectAndMembers() throws Exception {
		add(this.author, this.projectId, "reviewer@test.local").andExpect(status().isCreated());
		add(this.author, this.projectId, "outsider@test.local").andExpect(status().isCreated());

		this.mockMvc.perform(get("/api/projects").with(as(this.reviewer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(this.projectId))
			.andExpect(jsonPath("$[0].myRole").value("MEMBER"));
		this.mockMvc.perform(get("/api/projects/" + this.projectId).with(as(this.reviewer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("MEMBER"))
			.andExpect(jsonPath("$.owner.id").value(this.author.getId()));

		String body = this.mockMvc.perform(get("/api/projects/" + this.projectId + "/members").with(as(this.reviewer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].userId").value(this.author.getId()))
			.andExpect(jsonPath("$[0].role").value("OWNER"))
			.andExpect(jsonPath("$[0].addedAt").value(nullValue()))
			.andExpect(jsonPath("$[1].userId").value(this.reviewer.getId()))
			.andExpect(jsonPath("$[1].role").value("MEMBER"))
			.andExpect(jsonPath("$[2].userId").value(this.outsider.getId()))
			.andExpect(jsonPath("$[0].email").doesNotExist())
			.andExpect(jsonPath("$[1].email").doesNotExist())
			.andExpect(jsonPath("$[1].*", hasSize(4)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("@test.local");
	}

	// AC-MEMBER-02: 목록은 Owner·MEMBER 프로젝트를 created_at DESC, id DESC로 반환
	@Test
	void listIncludesOwnedAndMemberProjectsInOrder() throws Exception {
		long reviewerProject = createProject(this.reviewer, "Reviewer 프로젝트");
		long outsiderProject = createProject(this.outsider, "Outsider 프로젝트");
		add(this.reviewer, reviewerProject, "author@test.local").andExpect(status().isCreated());
		setCreatedAt(this.projectId, "2026-01-01T00:00:00Z");
		setCreatedAt(reviewerProject, "2026-01-02T00:00:00Z");
		setCreatedAt(outsiderProject, "2026-01-03T00:00:00Z");

		this.mockMvc.perform(get("/api/projects").with(as(this.author)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(reviewerProject))
			.andExpect(jsonPath("$[0].myRole").value("MEMBER"))
			.andExpect(jsonPath("$[1].id").value(this.projectId))
			.andExpect(jsonPath("$[1].myRole").value("OWNER"));
	}

	// AC-MEMBER-06: MEMBER는 추가·제거 불가
	@Test
	void memberCannotAddOrRemoveMembers() throws Exception {
		add(this.author, this.projectId, "reviewer@test.local").andExpect(status().isCreated());
		add(this.author, this.projectId, "outsider@test.local").andExpect(status().isCreated());

		add(this.reviewer, this.projectId, "author@test.local").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		remove(this.reviewer, this.projectId, this.outsider.getId()).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		assertThat(memberCount()).isEqualTo(2);
	}

	// AC-MEMBER-07, 17: 비구성원은 유효한 요청에서 미존재 프로젝트와 같은 404
	@Test
	void nonMemberCannotDiscoverProject() throws Exception {
		long missing = this.projectId + 1000;
		add(this.author, this.projectId, "reviewer@test.local").andExpect(status().isCreated());

		assertSameNotFound(get("/api/projects/" + this.projectId), get("/api/projects/" + missing));
		assertSameNotFound(get("/api/projects/" + this.projectId + "/members"),
				get("/api/projects/" + missing + "/members"));
		assertSameNotFound(
				post("/api/projects/" + this.projectId + "/members").with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(emailBody("outsider@test.local")),
				post("/api/projects/" + missing + "/members").with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(emailBody("outsider@test.local")));
		assertSameNotFound(delete("/api/projects/" + this.projectId + "/members/" + this.reviewer.getId()).with(csrf()),
				delete("/api/projects/" + missing + "/members/" + this.reviewer.getId()).with(csrf()));

		this.mockMvc.perform(get("/api/projects").with(as(this.outsider)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
		assertThat(memberCount()).isEqualTo(1);
	}

	// AC-MEMBER-05, 08: 미가입·비활성 이메일은 같은 404, 응답에 이메일 없음
	@Test
	void unknownAndDisabledEmailsGetSameResponse() throws Exception {
		this.jdbcTemplate.update("UPDATE users SET enabled = false WHERE id = ?", this.outsider.getId());

		String unknown = add(this.author, this.projectId, "nobody@test.local").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String disabled = add(this.author, this.projectId, "outsider@test.local").andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(unknown).isEqualTo(disabled).doesNotContain("nobody").doesNotContain("outsider");
		assertThat(memberCount()).isZero();
	}

	// AC-MEMBER-05, 09: 이미 MEMBER, Owner 자신은 409, 응답에 이메일 없음
	@Test
	void existingMemberAndOwnerCannotBeAdded() throws Exception {
		add(this.author, this.projectId, "reviewer@test.local").andExpect(status().isCreated());

		String duplicate = add(this.author, this.projectId, "reviewer@test.local").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ALREADY_EXISTS"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		add(this.author, this.projectId, "author@test.local").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ALREADY_EXISTS"));

		assertThat(duplicate).doesNotContain("reviewer@");
		assertThat(memberCount()).isEqualTo(1);
	}

	// AC-MEMBER-05, 15: 이메일 검증 400, 입력값 미포함
	@Test
	void invalidEmailIsRejected() throws Exception {
		for (String content : List.of("{}", "{\"email\":null}", emailBody("   "), emailBody("not-an-email"),
				emailBody("a".repeat(250) + "@x.com"))) {
			String body = this.mockMvc
				.perform(post("/api/projects/" + this.projectId + "/members").with(as(this.author))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(content))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(body).doesNotContain("not-an-email").doesNotContain("@x.com");
		}
		assertThat(memberCount()).isZero();
	}

	// AC-MEMBER-12: 제거 204, 제거된 사용자는 목록에서 빠지고 404
	@Test
	void ownerRemovesMemberAndAccessIsRevoked() throws Exception {
		add(this.author, this.projectId, "reviewer@test.local").andExpect(status().isCreated());

		remove(this.author, this.projectId, this.reviewer.getId()).andExpect(status().isNoContent());

		assertThat(memberCount()).isZero();
		this.mockMvc.perform(get("/api/projects").with(as(this.reviewer)))
			.andExpect(jsonPath("$", hasSize(0)));
		this.mockMvc.perform(get("/api/projects/" + this.projectId).with(as(this.reviewer)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/members").with(as(this.reviewer)))
			.andExpect(status().isNotFound());
	}

	// AC-MEMBER-13: 같은 표시 이름 중 지정한 userId만 제거, 다른 프로젝트 행 보호
	@Test
	void removalTargetsExactUserId() throws Exception {
		User twinA = this.userAccountService.createUser("twin-a@test.local", PASSWORD, "Same Name");
		User twinB = this.userAccountService.createUser("twin-b@test.local", PASSWORD, "Same Name");
		add(this.author, this.projectId, "twin-a@test.local").andExpect(status().isCreated());
		add(this.author, this.projectId, "twin-b@test.local").andExpect(status().isCreated());
		long otherProject = createProject(this.reviewer, "Reviewer 프로젝트");
		add(this.reviewer, otherProject, "outsider@test.local").andExpect(status().isCreated());

		remove(this.author, this.projectId, twinB.getId()).andExpect(status().isNoContent());
		remove(this.author, this.projectId, this.outsider.getId()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_NOT_FOUND"));

		assertThat(this.jdbcTemplate.queryForList("SELECT user_id FROM project_members WHERE project_id = ?",
				Long.class, this.projectId))
			.containsExactly(twinA.getId());
		assertThat(this.jdbcTemplate.queryForList("SELECT user_id FROM project_members WHERE project_id = ?",
				Long.class, otherProject))
			.containsExactly(this.outsider.getId());
	}

	// AC-MEMBER-14: Owner 제거 400, 비구성원 제거 404
	@Test
	void ownerCannotBeRemovedAndNonMemberRemovalIsNotFound() throws Exception {
		remove(this.author, this.projectId, this.author.getId()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PROJECT_OWNER_NOT_REMOVABLE"));
		remove(this.author, this.projectId, this.reviewer.getId()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_MEMBER_NOT_FOUND"));
	}

	// AC-MEMBER-15: 미인증 401, CSRF 없음 403, 숫자가 아닌 경로 변수 400
	@Test
	void authenticationCsrfAndPathVariableErrors() throws Exception {
		add(this.author, this.projectId, "reviewer@test.local").andExpect(status().isCreated());

		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/members"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		this.mockMvc
			.perform(post("/api/projects/" + this.projectId + "/members").with(as(this.author))
				.contentType(MediaType.APPLICATION_JSON)
				.content(emailBody("outsider@test.local")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		this.mockMvc
			.perform(delete("/api/projects/" + this.projectId + "/members/" + this.reviewer.getId())
				.with(as(this.author)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		this.mockMvc.perform(get("/api/projects/abc/members").with(as(this.author)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		this.mockMvc
			.perform(delete("/api/projects/" + this.projectId + "/members/abc").with(as(this.author)).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));

		assertThat(memberCount()).isEqualTo(1);
	}

	private void assertSameNotFound(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder existing,
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder missing) throws Exception {
		String forExisting = this.mockMvc.perform(existing.with(as(this.outsider)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String forMissing = this.mockMvc.perform(missing.with(as(this.outsider)))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(forExisting).isEqualTo(forMissing);
	}

	private ResultActions add(User user, long projectId, String email) throws Exception {
		return this.mockMvc.perform(post("/api/projects/" + projectId + "/members").with(as(user))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(emailBody(email)));
	}

	private ResultActions remove(User user, long projectId, long userId) throws Exception {
		return this.mockMvc
			.perform(delete("/api/projects/" + projectId + "/members/" + userId).with(as(user)).with(csrf()));
	}

	private long createProject(User owner, String name) throws Exception {
		String location = this.mockMvc
			.perform(post("/api/projects").with(as(owner))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.jsonMapper.writeValueAsString(Map.of("name", name))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.myRole").value("OWNER"))
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private void setCreatedAt(long projectId, String instant) {
		this.jdbcTemplate.update("UPDATE projects SET created_at = ? WHERE id = ?",
				Timestamp.from(Instant.parse(instant)), projectId);
	}

	private int memberCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM project_members", Integer.class);
	}

	private String emailBody(String email) {
		Map<String, Object> map = new HashMap<>();
		map.put("email", email);
		return this.jsonMapper.writeValueAsString(map);
	}

}
