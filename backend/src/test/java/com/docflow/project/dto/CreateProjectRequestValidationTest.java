package com.docflow.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SPEC-01 §5.5.5 입력 테스트 케이스 #1~#17.
 */
class CreateProjectRequestValidationTest {

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

	private static Set<String> invalidFields(CreateProjectRequest request) {
		Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(request);
		return violations.stream().map((v) -> v.getPropertyPath().toString()).collect(Collectors.toSet());
	}

	private static Set<String> messagesFor(CreateProjectRequest request, String field) {
		return validator.validate(request)
			.stream()
			.filter((v) -> v.getPropertyPath().toString().equals(field))
			.map(ConstraintViolation::getMessage)
			.collect(Collectors.toSet());
	}

	// #1
	@Test
	void case01_missingNullEmptyOrBlankNameIsRequired() {
		for (String name : new String[] { null, "", "   " }) {
			CreateProjectRequest request = new CreateProjectRequest(name, null);
			assertThat(messagesFor(request, "name")).containsExactly("프로젝트 이름을 입력해 주세요.");
		}
	}

	// #2
	@Test
	void case02_nameOfOnlyNbspIdeographicSpaceAndTabIsRequired() {
		CreateProjectRequest request = new CreateProjectRequest(" 　\t", null);
		assertThat(request.name()).isEmpty();
		assertThat(messagesFor(request, "name")).containsExactly("프로젝트 이름을 입력해 주세요.");
	}

	// #3
	@Test
	void case03_surroundingSpacesAreTrimmed() {
		CreateProjectRequest request = new CreateProjectRequest("  DocFlow 기획  ", null);
		assertThat(request.name()).isEqualTo("DocFlow 기획");
		assertThat(invalidFields(request)).isEmpty();
	}

	// #4
	@Test
	void case04_surroundingNbspIsTrimmed() {
		CreateProjectRequest request = new CreateProjectRequest(" DocFlow ", null);
		assertThat(request.name()).isEqualTo("DocFlow");
		assertThat(invalidFields(request)).isEmpty();
	}

	// #5
	@Test
	void case05_innerIdeographicSpaceIsKept() {
		CreateProjectRequest request = new CreateProjectRequest("Doc　Flow", null);
		assertThat(request.name()).isEqualTo("Doc　Flow");
		assertThat(invalidFields(request)).isEmpty();
	}

	// #6, #7, #8, #9
	@Test
	void case06to09_forbiddenCharactersInNameAreRejected() {
		for (String name : new String[] { "Doc\nFlow", "Doc\tFlow", "Doc\u0000Flow", "Doc​Flow", "﻿DocFlow",
				"Doc‮Flow", "Doc⁦Flow", "‍" }) {
			assertThat(messagesFor(new CreateProjectRequest(name, null), "name"))
				.as("name=%s", name.codePoints().mapToObj(Integer::toHexString).toList())
				.containsExactly("프로젝트 이름에 사용할 수 없는 문자가 포함되어 있습니다.");
		}
	}

	// #10
	@Test
	void case10_zwjEmojiSequenceIsAllowed() {
		assertThat(invalidFields(new CreateProjectRequest("👨‍👩‍👧", null))).isEmpty();
	}

	// #11
	@Test
	void case11_nameLengthBoundaryIs100Utf16Units() {
		assertThat(invalidFields(new CreateProjectRequest("a".repeat(100), null))).isEmpty();
		assertThat(messagesFor(new CreateProjectRequest("a".repeat(101), null), "name"))
			.containsExactly("프로젝트 이름은 100자 이하로 입력해 주세요.");
	}

	// #12
	@Test
	void case12_lengthIsCheckedAfterNormalization() {
		CreateProjectRequest request = new CreateProjectRequest(" " + "a".repeat(100) + "  ", null);
		assertThat(request.name()).hasSize(100);
		assertThat(invalidFields(request)).isEmpty();
	}

	// #13
	@Test
	void case13_emojiCountsAsTwoUtf16Units() {
		assertThat(invalidFields(new CreateProjectRequest("😀".repeat(50), null))).isEmpty();
		assertThat(invalidFields(new CreateProjectRequest("😀".repeat(51), null))).containsExactly("name");
	}

	// #14
	@Test
	void case14_missingNullEmptyOrBlankDescriptionBecomesNull() {
		for (String description : new String[] { null, "", "  \n " }) {
			CreateProjectRequest request = new CreateProjectRequest("DocFlow", description);
			assertThat(request.description()).isNull();
			assertThat(invalidFields(request)).isEmpty();
		}
	}

	// #15
	@Test
	void case15_descriptionKeepsInnerLineBreaksAndTabs() {
		CreateProjectRequest request = new CreateProjectRequest("DocFlow", "1행\n\t2행\r\n3행");
		assertThat(request.description()).isEqualTo("1행\n\t2행\r\n3행");
		assertThat(invalidFields(request)).isEmpty();
	}

	// #16
	@Test
	void case16_otherControlCharactersInDescriptionAreRejected() {
		for (String description : new String[] { "설명\u0000", "설명\u0007" }) {
			assertThat(messagesFor(new CreateProjectRequest("DocFlow", description), "description"))
				.containsExactly("설명에 사용할 수 없는 문자가 포함되어 있습니다.");
		}
	}

	// #17
	@Test
	void case17_descriptionLengthBoundaryIs1000Utf16Units() {
		assertThat(invalidFields(new CreateProjectRequest("DocFlow", "d".repeat(1000)))).isEmpty();
		assertThat(messagesFor(new CreateProjectRequest("DocFlow", "d".repeat(1001)), "description"))
			.containsExactly("설명은 1000자 이하로 입력해 주세요.");
	}

}
