package com.docflow.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRequestTest {

	@Test
	void toStringMasksPassword() {
		LoginRequest request = new LoginRequest("author@docflow.local", "Secret-Password-1");

		assertThat(request.toString()).doesNotContain("Secret-Password-1").contains("****");
	}

}
