package com.docflow.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.docflow.project.dto.CreateProjectRequest;
import com.docflow.project.dto.ProjectResponse;
import com.docflow.project.service.ProjectService;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * AC-PROJECT-16: 같은 사용자의 동시 생성 요청은 각각 독립적으로 성공한다.
 */
@SpringBootTest
class ProjectConcurrencyIntegrationTest {

	@Autowired
	private ProjectService projectService;

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
	void concurrentCreatesBySameUserBothSucceed() throws Exception {
		User owner = this.userAccountService.createUser("author@test.local", "Correct-Horse-Battery-9", "Author");
		CountDownLatch start = new CountDownLatch(1);
		Callable<ProjectResponse> task = () -> {
			start.await();
			return this.projectService.create(owner.getId(), new CreateProjectRequest("같은 이름", null));
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<ProjectResponse>> futures = List.of(executor.submit(task), executor.submit(task));
			start.countDown();
			ProjectResponse first = futures.get(0).get(10, TimeUnit.SECONDS);
			ProjectResponse second = futures.get(1).get(10, TimeUnit.SECONDS);

			assertThat(first.id()).isNotEqualTo(second.id());
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM projects WHERE owner_id = ?", Integer.class,
				owner.getId()))
			.isEqualTo(2);
	}

}
