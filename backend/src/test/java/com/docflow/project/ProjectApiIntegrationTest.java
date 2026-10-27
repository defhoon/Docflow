package com.docflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-01 프로젝트 API 통합 테스트 (실제 PostgreSQL, 보안 설정 활성 상태).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ProjectApiIntegrationTest {

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

	@BeforeEach
	void setUp() {
		cleanUp();
		this.author = this.userAccountService.createUser("author@test.local", PASSWORD, "Author");
		this.reviewer = this.userAccountService.createUser("reviewer@test.local", PASSWORD, "Reviewer");
	}

	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM projects");
		this.jdbcTemplate.update("DELETE FROM users");
	}

	// AC-PROJECT-01, 02, 14: 생성, Owner = 세션 사용자, 응답 필드
	@Test
	void createReturnsCreatedProjectOwnedBySessionUser() throws Exception {
		String location = create(this.author, body("DocFlow", "설명")).andExpect(status().isCreated())
			.andExpect(header().string("Location", matchesPattern("/api/projects/\\d+")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("DocFlow"))
			.andExpect(jsonPath("$.description").value("설명"))
			.andExpect(jsonPath("$.owner.id").value(this.author.getId()))
			.andExpect(jsonPath("$.owner.displayName").value("Author"))
			.andExpect(jsonPath("$.owner.*", hasSize(2)))
			.andExpect(jsonPath("$.myRole").value("OWNER"))
			.andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.+Z")))
			.andExpect(jsonPath("$.updatedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.+Z")))
			.andReturn()
			.getResponse()
			.getHeader("Location");

		Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		assertThat(this.jdbcTemplate.queryForObject("SELECT owner_id FROM projects WHERE id = ?", Long.class, id))
			.isEqualTo(this.author.getId());
	}

	// AC-PROJECT-01: 실제 로그인 세션과 CSRF로 생성
	@Test
	void createWorksWithRealLoginSession() throws Exception {
		MockHttpSession session = new MockHttpSession();
		this.mockMvc
			.perform(post("/api/auth/login").session(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"author@test.local\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk());

		this.mockMvc
			.perform(post("/api/projects").session(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("세션 프로젝트", null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.owner.id").value(this.author.getId()));
	}

	// AC-PROJECT-03: ownerId 위조 무시
	@Test
	void ownerIdInBodyIsIgnored() throws Exception {
		Map<String, Object> forged = new HashMap<>();
		forged.put("name", "위조 시도");
		forged.put("ownerId", this.reviewer.getId());
		forged.put("owner", Map.of("id", this.reviewer.getId()));

		create(this.author, this.jsonMapper.writeValueAsString(forged)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.owner.id").value(this.author.getId()));

		assertThat(this.jdbcTemplate.queryForObject("SELECT owner_id FROM projects", Long.class))
			.isEqualTo(this.author.getId());
	}

	// AC-PROJECT-04: 정규화 저장 (§5.5.5 #3, #4, #5, #14, #15)
	@Test
	void namesAndDescriptionsAreNormalizedBeforeSaving() throws Exception {
		create(this.author, body("  DocFlow 기획  ", "  설명  ")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("DocFlow 기획"))
			.andExpect(jsonPath("$.description").value("설명"));
		create(this.author, body(" DocFlow ", "  \n ")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("DocFlow"))
			.andExpect(jsonPath("$.description").doesNotExist());
		create(this.author, body("Doc　Flow", "1행\n\t2행\r\n3행")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Doc　Flow"))
			.andExpect(jsonPath("$.description").value("1행\n\t2행\r\n3행"));
		create(this.author, "{\"name\":\"설명 없음\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").doesNotExist());

		assertThat(this.jdbcTemplate.queryForList("SELECT description FROM projects ORDER BY id", String.class))
			.containsExactly("설명", null, "1행\n\t2행\r\n3행", null);
	}

	// AC-PROJECT-05: 필수 (§5.5.5 #1, #2)
	@Test
	void missingNullEmptyOrBlankNameIsRejected() throws Exception {
		for (String content : List.of("{}", "{\"name\":null}", body("", null), body("   ", null),
				body(" 　\t", null))) {
			create(this.author, content).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("프로젝트 이름을 입력해 주세요."));
		}
		assertThat(projectCount()).isZero();
	}

	// AC-PROJECT-05: 허용 문자 (§5.5.5 #6~#9, #16), 응답에 입력값 미포함
	@Test
	void forbiddenCharactersAreRejectedWithoutEchoingInput() throws Exception {
		for (String name : List.of("Doc\nFlow", "Doc\tFlow", "Doc\u0000Flow", "Doc​Flow", "﻿DocFlow",
				"Doc‮Flow", "Doc⁦Flow", "‍")) {
			String response = create(this.author, body(name, null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(response).doesNotContain("Doc");
		}
		for (String description : List.of("설명\u0000", "설명\u0007")) {
			create(this.author, body("DocFlow", description)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
		}
		assertThat(projectCount()).isZero();
	}

	// AC-PROJECT-05: 허용 (§5.5.5 #10)
	@Test
	void zwjEmojiSequenceIsAccepted() throws Exception {
		create(this.author, body("👨‍👩‍👧", null)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("👨‍👩‍👧"));
	}

	// AC-PROJECT-06: 정규화 후 UTF-16 길이 (§5.5.5 #11~#13, #17)
	@Test
	void lengthIsCheckedAfterNormalizationInUtf16Units() throws Exception {
		create(this.author, body("a".repeat(100), null)).andExpect(status().isCreated());
		create(this.author, body("a".repeat(101), null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
		create(this.author, body("   " + "b".repeat(100), null)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("b".repeat(100)));
		create(this.author, body("😀".repeat(50), null)).andExpect(status().isCreated());
		create(this.author, body("😀".repeat(51), null)).andExpect(status().isBadRequest());
		create(this.author, body("DocFlow", "d".repeat(1000))).andExpect(status().isCreated());
		create(this.author, body("DocFlow", "d".repeat(1001))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("description"));

		assertThat(projectCount()).isEqualTo(4);
	}

	// AC-PROJECT-07: 미인증 401 JSON
	@Test
	void unauthenticatedRequestsAreRejected() throws Exception {
		this.mockMvc
			.perform(post("/api/projects").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("DocFlow", null)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
			.andExpect(header().doesNotExist("Location"));
		this.mockMvc.perform(get("/api/projects"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		this.mockMvc.perform(get("/api/projects/1"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		assertThat(projectCount()).isZero();
	}

	// AC-PROJECT-08: CSRF 없음 403
	@Test
	void createWithoutCsrfIsRejected() throws Exception {
		this.mockMvc
			.perform(post("/api/projects").with(as(this.author))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("DocFlow", null)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		assertThat(projectCount()).isZero();
	}

	// AC-PROJECT-09: 본인 것만, created_at DESC, id DESC
	@Test
	void listReturnsOwnProjectsOrderedByCreatedAtDescThenIdDesc() throws Exception {
		long oldest = createdId(this.author, "oldest");
		long tieFirst = createdId(this.author, "tie-first");
		long tieSecond = createdId(this.author, "tie-second");
		long newest = createdId(this.author, "newest");
		createdId(this.reviewer, "reviewer-project");

		setCreatedAt(oldest, "2026-01-01T00:00:00Z");
		setCreatedAt(tieFirst, "2026-01-02T00:00:00Z");
		setCreatedAt(tieSecond, "2026-01-02T00:00:00Z");
		setCreatedAt(newest, "2026-01-03T00:00:00Z");

		this.mockMvc.perform(get("/api/projects").with(as(this.author)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[0].id").value(newest))
			.andExpect(jsonPath("$[1].id").value(tieSecond))
			.andExpect(jsonPath("$[2].id").value(tieFirst))
			.andExpect(jsonPath("$[3].id").value(oldest))
			.andExpect(jsonPath("$[0].owner").doesNotExist())
			.andExpect(jsonPath("$[0].myRole").value("OWNER"));
	}

	// AC-PROJECT-10: 빈 목록
	@Test
	void listIsEmptyArrayWhenUserHasNoProjects() throws Exception {
		createdId(this.reviewer, "reviewer-project");

		this.mockMvc.perform(get("/api/projects").with(as(this.author)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	// AC-PROJECT-11, 12: 본인 200, 타인·미존재 동일 404
	@Test
	void detailIsVisibleOnlyToOwnerAndHidesExistence() throws Exception {
		long id = createdId(this.author, "Author 프로젝트");

		this.mockMvc.perform(get("/api/projects/" + id).with(as(this.author)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.owner.displayName").value("Author"))
			.andExpect(jsonPath("$.myRole").value("OWNER"));

		String otherUser = this.mockMvc.perform(get("/api/projects/" + id).with(as(this.reviewer)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String missing = this.mockMvc.perform(get("/api/projects/" + (id + 1000)).with(as(this.reviewer)))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(otherUser).isEqualTo(missing);
	}

	// AC-PROJECT-13: 숫자가 아닌·범위 초과 ID → 400, ERROR 로그 없음
	@Test
	void nonNumericProjectIdIsBadRequest(CapturedOutput output) throws Exception {
		for (String id : List.of("abc", "99999999999999999999")) {
			this.mockMvc.perform(get("/api/projects/" + id).with(as(this.author)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message").isString());
		}
		assertThat(output.getAll()).doesNotContain("Unhandled exception");
	}

	// §6.5 오류 계약: 잘못된 JSON·본문 누락 → 400 VALIDATION_FAILED (fieldErrors 없음), Content-Type → 415
	@Test
	void malformedOrMissingBodyAndWrongContentType() throws Exception {
		create(this.author, "{\"name\":").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors").doesNotExist());
		this.mockMvc
			.perform(post("/api/projects").with(as(this.author)).with(csrf()).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		this.mockMvc
			.perform(post("/api/projects").with(as(this.author))
				.with(csrf())
				.contentType(MediaType.TEXT_PLAIN)
				.content("DocFlow"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		assertThat(projectCount()).isZero();
	}

	private ResultActions create(User user, String content) throws Exception {
		return this.mockMvc.perform(post("/api/projects").with(as(user))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(content));
	}

	private long createdId(User user, String name) throws Exception {
		String location = create(user, body(name, null)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private void setCreatedAt(long projectId, String instant) {
		this.jdbcTemplate.update("UPDATE projects SET created_at = ? WHERE id = ?",
				Timestamp.from(Instant.parse(instant)), projectId);
	}

	private int projectCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM projects", Integer.class);
	}

	private String body(String name, String description) {
		Map<String, Object> map = new HashMap<>();
		map.put("name", name);
		map.put("description", description);
		return this.jsonMapper.writeValueAsString(map);
	}

	static RequestPostProcessor as(User user) {
		return authentication(
				UsernamePasswordAuthenticationToken.authenticated(AuthenticatedUser.from(user), null, List.of()));
	}

}
