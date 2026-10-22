package com.docflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.user.domain.User;
import com.docflow.user.repository.UserRepository;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-AUTH Backend Acceptance Criteria 통합 테스트 (실제 PostgreSQL, 보안 설정 활성 상태).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthApiIntegrationTest {

	private static final String EMAIL = "author@test.local";

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	private static final String WRONG_PASSWORD = "Wrong-Password-Value-7";

	private static final String CONTEXT_KEY = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		this.jdbcTemplate.update("DELETE FROM users");
		this.userAccountService.createUser(EMAIL, PASSWORD, "Author");
	}

	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM users");
	}

	// AC-AUTH-01, 10: 로그인 성공, SecurityContext 명시적 저장, /me 조회, 민감 필드 미포함
	@Test
	void loginStoresSecurityContextInSessionAndMeReturnsUser() throws Exception {
		MockHttpSession session = new MockHttpSession();

		login(EMAIL, PASSWORD, session).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.displayName").value("Author"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist());

		Object context = session.getAttribute(CONTEXT_KEY);
		assertThat(context).isInstanceOf(SecurityContext.class);
		Object principal = ((SecurityContext) context).getAuthentication().getPrincipal();
		assertThat(principal).isInstanceOf(AuthenticatedUser.class);
		assertThat(((AuthenticatedUser) principal).getPassword()).isNull();

		this.mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	// 이메일 정규화: 앞뒤 공백·대소문자 차이
	@Test
	void loginNormalizesEmail() throws Exception {
		login("  AUTHOR@Test.Local ", PASSWORD, new MockHttpSession()).andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL));
	}

	// AC-AUTH-02: 세션 고정 방어
	@Test
	void loginChangesSessionId() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String sessionIdBeforeLogin = session.getId();

		login(EMAIL, PASSWORD, session).andExpect(status().isOk());

		assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
	}

	// AC-AUTH-03: 계정 존재 여부를 드러내지 않는 동일한 실패 응답
	@Test
	void invalidCredentialsReturnIdenticalUnauthorizedResponses() throws Exception {
		User disabled = this.userAccountService.createUser("disabled@test.local", PASSWORD, "Disabled");
		disabled.disable();
		this.userRepository.save(disabled);

		MockHttpSession unknownSession = new MockHttpSession();
		MockHttpSession wrongPasswordSession = new MockHttpSession();
		MockHttpSession disabledSession = new MockHttpSession();

		String unknown = login("nobody@test.local", PASSWORD, unknownSession).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String wrongPassword = login(EMAIL, WRONG_PASSWORD, wrongPasswordSession)
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String inactive = login("disabled@test.local", PASSWORD, disabledSession)
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(wrongPassword).isEqualTo(unknown);
		assertThat(inactive).isEqualTo(unknown);
		assertThat(unknownSession.getAttribute(CONTEXT_KEY)).isNull();
		assertThat(wrongPasswordSession.getAttribute(CONTEXT_KEY)).isNull();
		assertThat(disabledSession.getAttribute(CONTEXT_KEY)).isNull();
	}

	// AC-AUTH-04, 10: 입력 검증 실패, 응답에 입력 비밀번호 미포함
	@Test
	void invalidInputReturnsValidationErrorWithoutEchoingPassword() throws Exception {
		String tooLongPassword = "P".repeat(129);

		String body = login(EMAIL, tooLongPassword, new MockHttpSession()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain(tooLongPassword);

		login("not-an-email", "", new MockHttpSession()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.length()").value(2));
	}

	// AC-AUTH-05: CSRF 보호 (login, logout)
	@Test
	void loginAndLogoutRequireValidCsrfToken() throws Exception {
		this.mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(EMAIL, PASSWORD)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		this.mockMvc
			.perform(post("/api/auth/login").with(csrf().asHeader().useInvalidToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(EMAIL, PASSWORD)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		this.mockMvc.perform(post("/api/auth/logout"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	// AC-AUTH-05, 06: 실제 SPA 흐름(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더)과 토큰 교체
	@Test
	void spaCsrfFlowWithCookieAndHeader() throws Exception {
		Cookie initialToken = fetchCsrfCookie(null);

		MvcResult loginResult = this.mockMvc
			.perform(post("/api/auth/login").cookie(initialToken)
				.header("X-XSRF-TOKEN", initialToken.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(EMAIL, PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(cookie().maxAge("XSRF-TOKEN", 0))
			.andReturn();
		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
		assertThat(session).isNotNull();

		Cookie rotatedToken = fetchCsrfCookie(session);
		assertThat(rotatedToken.getValue()).isNotEqualTo(initialToken.getValue());

		this.mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

		this.mockMvc
			.perform(post("/api/auth/logout").session(session)
				.cookie(rotatedToken)
				.header("X-XSRF-TOKEN", rotatedToken.getValue()))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge("XSRF-TOKEN", 0));
	}

	// AC-AUTH-06: CSRF 토큰 발급은 인증 없이 가능
	@Test
	void csrfEndpointIssuesTokenCookieWithoutAuthentication() throws Exception {
		this.mockMvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false))
			.andExpect(cookie().path("XSRF-TOKEN", "/"));
	}

	// AC-AUTH-07: 미인증 요청은 리다이렉트가 아닌 401 JSON
	@Test
	void unauthenticatedRequestsReceiveUnauthorizedJson() throws Exception {
		this.mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
			.andExpect(header().doesNotExist("Location"));

		this.mockMvc.perform(get("/api/projects"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	// AC-AUTH-08: 로그아웃 후 세션 무효, 세션 없는 로그아웃은 204
	@Test
	void logoutInvalidatesSessionAndIsIdempotent() throws Exception {
		MockHttpSession session = new MockHttpSession();
		login(EMAIL, PASSWORD, session).andExpect(status().isOk());

		this.mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge("JSESSIONID", 0));
		assertThat(session.isInvalid()).isTrue();

		this.mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

		this.mockMvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
	}

	// AC-AUTH-09: 무효화·만료된 세션
	@Test
	void invalidatedSessionIsRejected() throws Exception {
		MockHttpSession session = new MockHttpSession();
		login(EMAIL, PASSWORD, session).andExpect(status().isOk());

		session.invalidate();

		this.mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	// AC-AUTH-24: /me에서 비활성 계정 재확인 후 세션 무효화
	@Test
	void meRejectsDisabledUserAndInvalidatesSession() throws Exception {
		MockHttpSession session = new MockHttpSession();
		login(EMAIL, PASSWORD, session).andExpect(status().isOk());

		User user = this.userRepository.findByEmail(EMAIL).orElseThrow();
		user.disable();
		this.userRepository.save(user);

		this.mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		assertThat(session.isInvalid()).isTrue();
	}

	// AC-AUTH-11: 대소문자만 다른 이메일은 DB에서 거부
	@Test
	void databaseRejectsCaseInsensitiveDuplicateEmail() {
		assertThatThrownBy(() -> this.jdbcTemplate.update(
				"INSERT INTO users (email, password_hash, display_name) VALUES (?, ?, ?)", "AUTHOR@TEST.LOCAL",
				"{noop}x", "Duplicate"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	// AC-AUTH-15: 로그인 처리 중 비밀번호 원문이 로그에 출력되지 않음
	@Test
	void passwordsAreNotWrittenToLogs(CapturedOutput output) throws Exception {
		login(EMAIL, PASSWORD, new MockHttpSession()).andExpect(status().isOk());
		login(EMAIL, WRONG_PASSWORD, new MockHttpSession()).andExpect(status().isUnauthorized());

		assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(WRONG_PASSWORD);
	}

	private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
		return this.mockMvc.perform(post("/api/auth/login").session(session)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(email, password)));
	}

	private Cookie fetchCsrfCookie(MockHttpSession session) throws Exception {
		var request = get("/api/auth/csrf");
		if (session != null) {
			request.session(session);
		}
		Cookie token = this.mockMvc.perform(request)
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse()
			.getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		return token;
	}

	private static String json(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

}
