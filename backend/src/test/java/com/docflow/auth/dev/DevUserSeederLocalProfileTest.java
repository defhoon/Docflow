package com.docflow.auth.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.docflow.user.domain.User;
import com.docflow.user.repository.UserRepository;

// AC-AUTH-23: local 프로파일 Seeder. 비밀번호는 테스트 전용 값이며 실제 개발 비밀번호가 아니다.
@SpringBootTest(properties = "DEV_SEED_PASSWORD=" + DevUserSeederLocalProfileTest.SEED_PASSWORD)
@ActiveProfiles("local")
@ExtendWith(OutputCaptureExtension.class)
class DevUserSeederLocalProfileTest {

	static final String SEED_PASSWORD = "Test-Only-Seed-Password-42";

	@Autowired
	private DevUserSeeder devUserSeeder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void createsAccountsOnceWithoutChangingExistingPasswordsOrLoggingSecrets(CapturedOutput output) {
		this.jdbcTemplate.update("DELETE FROM users");

		this.devUserSeeder.run(new DefaultApplicationArguments());
		Map<String, String> hashesAfterFirstRun = passwordHashesByEmail();

		assertThat(hashesAfterFirstRun).containsOnlyKeys("author@docflow.local", "reviewer@docflow.local");
		hashesAfterFirstRun.values()
			.forEach((hash) -> assertThat(this.passwordEncoder.matches(SEED_PASSWORD, hash)).isTrue());

		this.devUserSeeder.run(new DefaultApplicationArguments());

		assertThat(passwordHashesByEmail()).isEqualTo(hashesAfterFirstRun);
		assertThat(output.getAll()).doesNotContain(SEED_PASSWORD);
		hashesAfterFirstRun.values().forEach((hash) -> assertThat(output.getAll()).doesNotContain(hash));
	}

	private Map<String, String> passwordHashesByEmail() {
		return this.userRepository.findAll()
			.stream()
			.collect(Collectors.toMap(User::getEmail, User::getPasswordHash));
	}

}
