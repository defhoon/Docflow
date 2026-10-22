package com.docflow.auth.dev;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.docflow.user.service.UserAccountService;

/**
 * {@code local} 프로파일 전용 개발 계정 생성기 (SPEC-AUTH §8).
 * <ul>
 * <li>비밀번호는 {@code DEV_SEED_PASSWORD}로만 받으며 Git과 로그에 남기지 않는다.</li>
 * <li>이미 존재하는 계정은 건너뛰며 기존 비밀번호를 변경하지 않는다.</li>
 * <li>Author/Reviewer 구분은 테스트 시나리오용이며 전역 Role이 아니다.</li>
 * </ul>
 */
@Component
@Profile("local")
public class DevUserSeeder implements ApplicationRunner {

	static final List<DevAccount> ACCOUNTS = List.of(new DevAccount("author@docflow.local", "Author"),
			new DevAccount("reviewer@docflow.local", "Reviewer"));

	private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

	private final UserAccountService userAccountService;

	private final String seedPassword;

	public DevUserSeeder(UserAccountService userAccountService,
			@Value("${DEV_SEED_PASSWORD:}") String seedPassword) {
		this.userAccountService = userAccountService;
		this.seedPassword = seedPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(this.seedPassword)) {
			log.warn("DEV_SEED_PASSWORD is not set. Development accounts were not created.");
			return;
		}
		for (DevAccount account : ACCOUNTS) {
			if (this.userAccountService.existsByEmail(account.email())) {
				log.info("Development account already exists, skipped: {}", account.email());
				continue;
			}
			this.userAccountService.createUser(account.email(), this.seedPassword, account.displayName());
			log.info("Development account created: {}", account.email());
		}
	}

	record DevAccount(String email, String displayName) {
	}

}
