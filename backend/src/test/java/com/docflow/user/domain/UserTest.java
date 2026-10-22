package com.docflow.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	void normalizesEmailByTrimmingAndLowerCasing() {
		assertThat(User.normalizeEmail("  Author@DocFlow.Local ")).isEqualTo("author@docflow.local");
	}

	@Test
	void createStoresNormalizedEmailAndIsEnabled() {
		User user = User.create(" Reviewer@Example.COM", "{bcrypt}hash", "Reviewer");

		assertThat(user.getEmail()).isEqualTo("reviewer@example.com");
		assertThat(user.isEnabled()).isTrue();
	}

	@Test
	void disableMakesUserInactive() {
		User user = User.create("a@example.com", "{bcrypt}hash", "A");

		user.disable();

		assertThat(user.isEnabled()).isFalse();
	}

}
