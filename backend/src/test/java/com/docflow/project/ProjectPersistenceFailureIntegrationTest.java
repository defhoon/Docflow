package com.docflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * AC-PROJECT-15: 실제 DB 저장 실패와 롤백 (SPEC-01 §10.2).
 * <p>
 * Mock이나 테스트 전용 트리거 없이 기존 FK 제약({@code owner_id → users.id}) 위반으로 실제 PostgreSQL 오류를
 * 일으킨다. 테스트 트랜잭션이 결과를 가리지 않도록 {@code @Transactional}을 사용하지 않고, 별도 커넥션
 * ({@link JdbcTemplate})으로 커밋된 DB 상태를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectPersistenceFailureIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM projects");
		this.jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void foreignKeyViolationRollsBackAndHidesInternalDetails() throws Exception {
		User owner = this.userAccountService.createUser("ghost@test.local", "Correct-Horse-Battery-9", "Ghost");
		// 세션(인증 정보)은 유지된 채 사용자 행만 사라진 비정상 상태를 만든다.
		this.jdbcTemplate.update("DELETE FROM users WHERE id = ?", owner.getId());
		int before = projectCount();

		String body = this.mockMvc
			.perform(post("/api/projects").with(ProjectApiIntegrationTest.as(owner))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"저장 실패\"}"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(projectCount()).isEqualTo(before);
		assertThat(body.toLowerCase()).doesNotContain("sql")
			.doesNotContain("error:")
			.doesNotContain("constraint")
			.doesNotContain("violat")
			.doesNotContain("fk")
			.doesNotContain("projects")
			.doesNotContain("users")
			.doesNotContain("exception");
	}

	private int projectCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM projects", Integer.class);
	}

}
