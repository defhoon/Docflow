package com.docflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.docflow.user.domain.User;

class AuthenticatedUserTest {

	@Test
	void eraseCredentialsRemovesPasswordHash() {
		AuthenticatedUser authenticatedUser = AuthenticatedUser.from(User.create("a@example.com", "{bcrypt}hash", "A"));

		authenticatedUser.eraseCredentials();

		assertThat(authenticatedUser.getPassword()).isNull();
		assertThat(authenticatedUser.getUsername()).isEqualTo("a@example.com");
	}

	@Test
	void hasNoGlobalAuthorities() {
		AuthenticatedUser authenticatedUser = AuthenticatedUser.from(User.create("a@example.com", "{bcrypt}hash", "A"));

		assertThat(authenticatedUser.getAuthorities()).isEmpty();
	}

	@Test
	void toStringDoesNotExposeEmailOrPasswordHash() {
		AuthenticatedUser authenticatedUser = AuthenticatedUser.from(User.create("a@example.com", "{bcrypt}hash", "A"));

		assertThat(authenticatedUser.toString()).doesNotContain("a@example.com").doesNotContain("hash");
	}

}
