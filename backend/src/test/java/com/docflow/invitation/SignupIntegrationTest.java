package com.docflow.invitation;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.invitation.service.AdminBootstrap;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** #26 초대코드 회원가입·관리자 발급·최초 관리자·로그인 시도 제한 통합 테스트. AC-INV-01~12. */
@SpringBootTest(properties = { "docflow.auth.attempts.signup-global-max-failures=1000" })
@AutoConfigureMockMvc
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class SignupIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AdminBootstrap adminBootstrap;

	private User admin;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.admin = this.userAccountService.createUser("admin@test.local", PASSWORD, "Admin");
		this.jdbcTemplate.update("UPDATE users SET system_role = 'ADMIN' WHERE id = ?", this.admin.getId());
	}

	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM invitations");
		this.jdbcTemplate.update("DELETE FROM users");
	}

	// AC-INV-01: 유효한 코드로 가입 → 201, bcrypt 해시, 코드 사용 처리, 로그인 가능
	@Test
	void signsUpWithValidCodeOnce() throws Exception {
		String code = issue(this.admin);

		signup(code, "  New@Test.Local ", "Strong-Passw0rd", " 새 사용자 ").andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("new@test.local"))
			.andExpect(jsonPath("$.displayName").value("새 사용자"))
			.andExpect(jsonPath("$.admin").value(false))
			.andExpect(jsonPath("$.password").doesNotExist());
		Map<String, Object> user = this.jdbcTemplate.queryForMap("SELECT * FROM users WHERE email = 'new@test.local'");
		assertThat((String) user.get("password_hash")).startsWith("{bcrypt}");
		assertThat(user.get("system_role")).isEqualTo("USER");
		Map<String, Object> invitation = this.jdbcTemplate.queryForMap("SELECT * FROM invitations");
		assertThat(invitation.get("used_by")).isEqualTo(user.get("id"));
		assertThat(invitation.get("used_at")).isNotNull();

		login("new@test.local", "Strong-Passw0rd").andExpect(status().isOk());
		// AC-INV-02: 재사용 거부
		signup(code, "other@test.local", "Strong-Passw0rd", "다른 사용자").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
	}

	// AC-INV-03: 없음·만료·폐기 코드는 같은 응답, 계정 미생성
	@Test
	void invalidExpiredAndRevokedCodesLookTheSame() throws Exception {
		String expired = issue(this.admin);
		this.jdbcTemplate.update("UPDATE invitations SET expires_at = now() - interval '1 second'");
		String revoked = issue(this.admin);
		Long revokedId = this.jdbcTemplate.queryForObject("SELECT max(id) FROM invitations", Long.class);
		this.mockMvc.perform(post("/api/admin/invitations/" + revokedId + "/revoke").with(as(this.admin)).with(csrf()))
			.andExpect(status().isNoContent());

		List<String> bodies = new ArrayList<>();
		int i = 0;
		for (String code : List.of(expired, revoked, "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GG", "x")) {
			bodies.add(signup(code, "u" + (i++) + "@test.local", "Strong-Passw0rd", "U").andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString());
		}
		assertThat(bodies).allMatch((body) -> body.equals(bodies.get(0)));
		assertThat(bodies.get(0)).contains("INVITATION_INVALID");
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1);
	}

	// AC-INV-04: 이미 가입된 이메일 409, 코드는 미사용 유지
	@Test
	void duplicateEmailKeepsCodeUnused() throws Exception {
		String code = issue(this.admin);

		signup(code, "ADMIN@test.local", "Strong-Passw0rd", "중복").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
		assertThat(this.jdbcTemplate.queryForObject("SELECT used_at FROM invitations", Object.class)).isNull();
		signup(code, "fresh@test.local", "Strong-Passw0rd", "새").andExpect(status().isCreated());
	}

	// AC-INV-05: 입력 검증(비밀번호 길이·72바이트, 이메일), CSRF 필요
	@Test
	void validatesInputAndRequiresCsrf() throws Exception {
		String code = issue(this.admin);

		signup(code, "a@test.local", "short", "A").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		signup(code, "not-an-email", "Strong-Passw0rd", "A").andExpect(status().isBadRequest());
		signup(code, "a@test.local", "가".repeat(25), "A").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		signup(code, "a@test.local", "Strong-Passw0rd", " ").andExpect(status().isBadRequest());
		this.mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content(body(code, "a@test.local", "Strong-Passw0rd", "A"))).andExpect(status().isForbidden());
		assertThat(this.jdbcTemplate.queryForObject("SELECT used_at FROM invitations", Object.class)).isNull();
	}

	// AC-INV-06: 같은 코드 동시 가입 → 정확히 하나만 성공
	@Test
	void concurrentSignupsWithSameCodeHaveSingleWinner() throws Exception {
		String code = issue(this.admin);
		int n = 6;
		CyclicBarrier barrier = new CyclicBarrier(n);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			String email = "c" + i + "@test.local";
			tasks.add(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return signup(code, email, "Strong-Passw0rd", "C").andReturn().getResponse().getStatus();
			});
		}
		ExecutorService executor = Executors.newFixedThreadPool(n);
		List<Integer> statuses = new ArrayList<>();
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> task : tasks) {
				futures.add(executor.submit(task));
			}
			for (Future<Integer> future : futures) {
				statuses.add(future.get(60, TimeUnit.SECONDS));
			}
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(statuses).filteredOn((s) -> s == 201).hasSize(1);
		assertThat(statuses).filteredOn((s) -> s == 400).hasSize(n - 1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(2);
	}

	// AC-INV-07: 가입 실패가 반복되면 429 + Retry-After
	@Test
	void repeatedSignupFailuresAreThrottled() throws Exception {
		for (int i = 0; i < 5; i++) {
			signup("WRONG-" + i, "guess@test.local", "Strong-Passw0rd", "G").andExpect(status().isBadRequest());
		}
		signup(issue(this.admin), "guess@test.local", "Strong-Passw0rd", "G").andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("AUTH_TOO_MANY_ATTEMPTS"));
	}

	// AC-INV-08: 관리자만 발급·조회·폐기. 목록에 코드·해시 없음, 발급 응답 no-store
	@Test
	void onlyAdminsManageInvitations() throws Exception {
		User user = this.userAccountService.createUser("user@test.local", PASSWORD, "User");
		this.mockMvc.perform(post("/api/admin/invitations").with(as(user)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		this.mockMvc.perform(get("/api/admin/invitations").with(as(user))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/api/admin/invitations")).andExpect(status().isUnauthorized());

		String issued = this.mockMvc.perform(post("/api/admin/invitations").with(as(this.admin)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"expiresInHours\":2,\"note\":\"홍길동\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andReturn().getResponse().getContentAsString();
		String code = this.jsonMapper.readTree(issued).get("code").asString();
		assertThat(code).matches("[0-9A-Z]{4}(-[0-9A-Z]{1,4}){6}");
		String hash = this.jdbcTemplate.queryForObject("SELECT code_hash FROM invitations", String.class);

		String list = this.mockMvc.perform(get("/api/admin/invitations").with(as(this.admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].note").value("홍길동"))
			.andExpect(jsonPath("$[0].status").value("ACTIVE"))
			.andReturn().getResponse().getContentAsString();
		assertThat(list).doesNotContain(code, hash);
		for (String bad : List.of("{\"expiresInHours\":0}", "{\"expiresInHours\":337}",
				"{\"note\":\"" + "x".repeat(101) + "\"}")) {
			this.mockMvc.perform(post("/api/admin/invitations").with(as(this.admin)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
		}
	}

	// AC-INV-09: 사용자가 없으면 최초 관리자 코드를 로그로 1회 발급, 그 코드로 가입하면 ADMIN
	@Test
	void bootstrapIssuesAdminCodeOnlyWhenNoUsersExist(CapturedOutput output) throws Exception {
		// 컨텍스트 시작 시점의 로그는 제외하고 이 테스트에서 출력된 부분만 본다.
		int start = output.toString().length();
		this.adminBootstrap.run(new DefaultApplicationArguments());
		assertThat(output.toString().substring(start)).doesNotContain("One-time administrator invitation code");

		this.jdbcTemplate.update("DELETE FROM users");
		this.adminBootstrap.run(new DefaultApplicationArguments());
		Matcher matcher = Pattern.compile("One-time administrator invitation code \\(valid [^)]+\\): (\\S+)")
			.matcher(output.toString().substring(start));
		assertThat(matcher.find()).isTrue();
		String code = matcher.group(1);

		String body = signup(code, "root@test.local", "Strong-Passw0rd", "운영자").andExpect(status().isCreated())
			.andExpect(jsonPath("$.admin").value(true))
			.andReturn().getResponse().getContentAsString();
		JsonNode created = this.jsonMapper.readTree(body);
		assertThat(this.jdbcTemplate.queryForObject("SELECT system_role FROM users WHERE id = ?", String.class,
				created.get("id").asLong())).isEqualTo("ADMIN");
	}

	// AC-INV-10: 로그인 실패 5회 후 올바른 비밀번호도 429, 다른 계정은 영향 없음, 성공 시 초기화
	@Test
	void loginIsThrottledPerAccount() throws Exception {
		this.userAccountService.createUser("victim@test.local", PASSWORD, "V");
		this.userAccountService.createUser("okay@test.local", PASSWORD, "O");

		login("okay@test.local", "wrong").andExpect(status().isUnauthorized());
		login("okay@test.local", PASSWORD).andExpect(status().isOk());
		for (int i = 0; i < 4; i++) {
			login("okay@test.local", "wrong").andExpect(status().isUnauthorized());
		}
		login("okay@test.local", PASSWORD).andExpect(status().isOk());

		for (int i = 0; i < 5; i++) {
			login("victim@test.local", "wrong").andExpect(status().isUnauthorized());
		}
		login(" VICTIM@test.local", PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("AUTH_TOO_MANY_ATTEMPTS"));
		login("admin@test.local", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(true));
	}

	private String issue(User issuer) throws Exception {
		String body = this.mockMvc.perform(post("/api/admin/invitations").with(as(issuer)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return this.jsonMapper.readTree(body).get("code").asString();
	}

	private ResultActions signup(String code, String email, String password, String displayName) throws Exception {
		return this.mockMvc.perform(post("/api/auth/signup").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(code, email, password, displayName)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return this.mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.jsonMapper.writeValueAsString(Map.of("email", email, "password", password))));
	}

	private String body(String code, String email, String password, String displayName) {
		return this.jsonMapper.writeValueAsString(
				Map.of("inviteCode", code, "email", email, "password", password, "displayName", displayName));
	}

}
