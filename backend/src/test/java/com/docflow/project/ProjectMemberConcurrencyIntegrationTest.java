package com.docflow.project;

import static com.docflow.project.ProjectApiIntegrationTest.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.docflow.project.dto.CreateProjectRequest;
import com.docflow.project.service.ProjectService;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-02 §7.3 동시 추가와 DB 오류 구분 (AC-MEMBER-10, 11). 실제 PostgreSQL의 잠금 대기를 이용해 사전 확인을 통과한
 * 뒤에 제약 위반이 발생하는 상황을 결정적으로 만든다. 테스트 트랜잭션이 결과를 가리지 않도록 {@code @Transactional}을
 * 사용하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ProjectMemberConcurrencyIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DataSource dataSource;

	private User author;

	private User reviewer;

	private long projectId;

	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.author = this.userAccountService.createUser("author@test.local", PASSWORD, "Author");
		this.reviewer = this.userAccountService.createUser("reviewer@test.local", PASSWORD, "Reviewer");
		this.projectId = this.projectService.create(this.author.getId(), new CreateProjectRequest("DocFlow", null)).id();
		this.executor = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	void tearDown() {
		if (this.executor != null) {
			this.executor.shutdownNow();
		}
		cleanUp();
	}

	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM projects");
		this.jdbcTemplate.update("DELETE FROM users");
	}

	// AC-MEMBER-10: 같은 사용자 동시 추가 → 201 1건 + 409 1건, 행 1건 (처리 경로와 무관한 결과 검증, 반복 실행)
	@Test
	void concurrentAddsOfSameUserResultInOneCreatedAndOneConflict() throws Exception {
		for (int round = 0; round < 5; round++) {
			this.jdbcTemplate.update("DELETE FROM project_members");
			CountDownLatch start = new CountDownLatch(1);
			Callable<MockHttpServletResponse> task = () -> {
				start.await();
				return addReviewer();
			};
			List<Future<MockHttpServletResponse>> futures = List.of(this.executor.submit(task),
					this.executor.submit(task));
			start.countDown();

			List<Integer> statuses = new ArrayList<>();
			for (Future<MockHttpServletResponse> future : futures) {
				MockHttpServletResponse response = future.get(30, TimeUnit.SECONDS);
				statuses.add(response.getStatus());
				if (response.getStatus() == 409) {
					assertThat(response.getContentAsString()).contains("PROJECT_MEMBER_ALREADY_EXISTS");
				}
			}
			assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(201, 409);
			assertThat(memberCount()).isEqualTo(1);
		}
	}

	// AC-MEMBER-11: 사전 확인을 통과한 뒤 실제 UNIQUE 위반 → 409 (UnexpectedRollbackException·500 아님)
	@Test
	void uniqueViolationAfterPreCheckIsConflict(CapturedOutput output) throws Exception {
		try (Connection blocker = this.dataSource.getConnection()) {
			blocker.setAutoCommit(false);
			try (PreparedStatement insert = blocker
				.prepareStatement("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)")) {
				insert.setLong(1, this.projectId);
				insert.setLong(2, this.reviewer.getId());
				insert.executeUpdate();
			}
			// 커밋 전이므로 요청의 사전 확인은 이 행을 보지 못하고, INSERT는 UNIQUE 인덱스에서 대기한다.
			Future<MockHttpServletResponse> request = this.executor.submit(this::addReviewer);
			awaitBlockedLock();
			blocker.commit();

			MockHttpServletResponse response = request.get(30, TimeUnit.SECONDS);
			assertThat(response.getStatus()).isEqualTo(409);
			assertThat(response.getContentAsString()).contains("PROJECT_MEMBER_ALREADY_EXISTS")
				.doesNotContain("uk_project_members")
				.doesNotContain("reviewer@");
		}
		assertThat(memberCount()).isEqualTo(1);
		assertThat(output.getAll()).doesNotContain("Unhandled exception").doesNotContain("UnexpectedRollbackException");
	}

	// AC-MEMBER-11: UNIQUE 외 DB 오류(FK 위반)는 409로 바꾸지 않고 500, 롤백
	@Test
	void otherDatabaseErrorIsInternalError() throws Exception {
		try (Connection blocker = this.dataSource.getConnection()) {
			blocker.setAutoCommit(false);
			try (PreparedStatement deleteUser = blocker.prepareStatement("DELETE FROM users WHERE id = ?")) {
				deleteUser.setLong(1, this.reviewer.getId());
				deleteUser.executeUpdate();
			}
			// 커밋 전이므로 요청은 사용자를 찾고, INSERT의 FK 확인이 삭제 중인 사용자 행에서 대기한다.
			Future<MockHttpServletResponse> request = this.executor.submit(this::addReviewer);
			awaitBlockedLock();
			blocker.commit();

			MockHttpServletResponse response = request.get(30, TimeUnit.SECONDS);
			assertThat(response.getStatus()).isEqualTo(500);
			assertThat(response.getContentAsString().toLowerCase()).contains("internal_error")
				.doesNotContain("already_exists")
				.doesNotContain("sql")
				.doesNotContain("constraint")
				.doesNotContain("project_members")
				.doesNotContain("fkey");
		}
		assertThat(memberCount()).isZero();
	}

	private MockHttpServletResponse addReviewer() throws Exception {
		return this.mockMvc
			.perform(post("/api/projects/" + this.projectId + "/members").with(as(this.author))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"reviewer@test.local\"}"))
			.andReturn()
			.getResponse();
	}

	/** 요청 트랜잭션이 잠금을 기다리기 시작할 때까지 기다린다. */
	private void awaitBlockedLock() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			Integer waiting = this.jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted",
					Integer.class);
			if (waiting != null && waiting > 0) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("요청이 잠금 대기 상태에 도달하지 않았습니다.");
	}

	private int memberCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM project_members", Integer.class);
	}

}
