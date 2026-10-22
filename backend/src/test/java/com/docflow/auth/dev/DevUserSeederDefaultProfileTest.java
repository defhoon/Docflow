package com.docflow.auth.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

// AC-AUTH-14: 기본 프로파일(테스트·CI)에서는 Seeder Bean이 등록되지 않는다.
@SpringBootTest
class DevUserSeederDefaultProfileTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void seederIsNotRegisteredWithoutLocalProfile() {
		assertThat(this.applicationContext.getBeanNamesForType(DevUserSeeder.class)).isEmpty();
	}

}
