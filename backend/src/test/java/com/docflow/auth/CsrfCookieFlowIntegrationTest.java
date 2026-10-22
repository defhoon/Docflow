package com.docflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import com.docflow.user.service.UserAccountService;

/**
 * 실제 SPA CSRF 흐름 검증 (XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더). 테스트 지원 기능 {@code csrf()}를
 * 사용하지 않는다.
 *
 * <p>
 * {@code csrf()}는 같은 테스트 컨텍스트의 CsrfFilter 토큰 저장소를 테스트용 저장소로 교체하므로, 각 테스트 전에
 * 애플리케이션의 실제 쿠키 저장소로 되돌린다. 보안 설정은 변경하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CsrfCookieFlowIntegrationTest {

	private static final String EMAIL = "author@test.local";

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private CookieCsrfTokenRepository csrfTokenRepository;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		WebTestUtils.setCsrfTokenRepository(new MockHttpServletRequest(this.webApplicationContext.getServletContext()),
				this.csrfTokenRepository);
		this.jdbcTemplate.update("DELETE FROM users");
		this.userAccountService.createUser(EMAIL, PASSWORD, "Author");
	}

	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM users");
	}

	// AC-AUTH-06: CSRF 토큰 발급은 인증 없이 가능, JS가 읽을 수 있는 쿠키
	@Test
	void csrfEndpointIssuesReadableTokenCookieWithoutAuthentication() throws Exception {
		this.mockMvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false))
			.andExpect(cookie().path("XSRF-TOKEN", "/"))
			.andExpect(cookie().attribute("XSRF-TOKEN", "SameSite", equalToIgnoringCase("Lax")));
	}

	// AC-AUTH-05, 06: 쿠키·헤더로 로그인, 로그인·로그아웃 시 토큰 삭제, 재발급 토큰은 이전과 다름
	@Test
	void loginAndLogoutWithCookieAndHeader() throws Exception {
		Cookie initialToken = fetchCsrfCookie(null);

		MvcResult loginResult = this.mockMvc
			.perform(post("/api/auth/login").cookie(initialToken)
				.header("X-XSRF-TOKEN", initialToken.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
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

		this.mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	// AC-AUTH-05: 쿠키와 헤더 값이 다르면 거부
	@Test
	void mismatchedHeaderIsRejected() throws Exception {
		Cookie token = fetchCsrfCookie(null);

		this.mockMvc
			.perform(post("/api/auth/login").cookie(token)
				.header("X-XSRF-TOKEN", "not-the-cookie-value")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
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

}
