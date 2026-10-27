package com.docflow.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AddMemberRequestValidationTest {

	private static ValidatorFactory factory;

	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	@Test
	void edgeWhitespaceIsStripped() {
		AddMemberRequest request = new AddMemberRequest("  reviewer@test.local \n");

		assertThat(request.email()).isEqualTo("reviewer@test.local");
		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void missingOrBlankEmailIsRequired() {
		assertThat(messages(new AddMemberRequest(null))).containsExactly("이메일을 입력해 주세요.");
		assertThat(messages(new AddMemberRequest("   "))).containsExactly("이메일을 입력해 주세요.");
	}

	@Test
	void malformedOrTooLongEmailIsRejected() {
		assertThat(messages(new AddMemberRequest("not-an-email"))).containsExactly("이메일 형식이 올바르지 않습니다.");
		String tooLong = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63);
		assertThat(tooLong).hasSize(256);
		assertThat(messages(new AddMemberRequest(tooLong))).contains("이메일은 255자 이하로 입력해 주세요.");
	}

	@Test
	void toStringDoesNotExposeEmail() {
		assertThat(new AddMemberRequest("reviewer@test.local").toString()).doesNotContain("reviewer");
	}

	private static Set<String> messages(AddMemberRequest request) {
		Set<ConstraintViolation<AddMemberRequest>> violations = validator.validate(request);
		return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
	}

}
