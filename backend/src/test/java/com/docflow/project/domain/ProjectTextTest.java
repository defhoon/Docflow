package com.docflow.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectTextTest {

	@Test
	void trimEdgesIsNullSafe() {
		assertThat(ProjectText.trimEdges(null)).isNull();
		assertThat(ProjectText.trimEdgesToNull(null)).isNull();
	}

	@Test
	void trimEdgesRemovesWhitespaceAndSpaceSeparatorsIncludingNbsp() {
		assertThat(ProjectText.trimEdges("  DocFlow 기획  ")).isEqualTo("DocFlow 기획");
		assertThat(ProjectText.trimEdges(" DocFlow ")).isEqualTo("DocFlow");
		assertThat(ProjectText.trimEdges("　\t\nDocFlow  ")).isEqualTo("DocFlow");
		assertThat(ProjectText.trimEdges(" 　\t")).isEmpty();
	}

	@Test
	void trimEdgesKeepsInnerCharacters() {
		assertThat(ProjectText.trimEdges(" Doc　  Flow ")).isEqualTo("Doc　  Flow");
	}

	@Test
	void trimEdgesToNullTurnsBlankIntoNull() {
		assertThat(ProjectText.trimEdgesToNull("")).isNull();
		assertThat(ProjectText.trimEdgesToNull("  \n ")).isNull();
		assertThat(ProjectText.trimEdgesToNull("  설명  ")).isEqualTo("설명");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Doc\nFlow", "Doc\tFlow", "Doc\rFlow", "Doc\u0000Flow", "Doc\u0007Flow", "Doc\u0085Flow",
			"Doc Flow", "Doc Flow", "Doc​Flow", "Doc⁠Flow", "﻿DocFlow", "Doc᠎Flow",
			"Doc‪Flow", "Doc‮Flow", "Doc⁦Flow", "Doc⁩Flow", "‍", "‌‍" })
	void nameRejectsControlInvisibleAndBidiCharacters(String name) {
		assertThat(ProjectText.isAllowedName(name)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "DocFlow 기획", "Doc　Flow", "Doc Flow", "프로젝트-1 (v2) #a", "👨‍👩‍👧",
			"Doc‍Flow", "Doc‌Flow", "‏مشروع", "😀" })
	void nameAllowsOrdinaryTextInnerSpacesAndJoiners(String name) {
		assertThat(ProjectText.isAllowedName(name)).isTrue();
	}

	@Test
	void nameRuleLeavesNullAndEmptyToRequiredValidation() {
		assertThat(ProjectText.isAllowedName(null)).isTrue();
		assertThat(ProjectText.isAllowedName("")).isTrue();
	}

	@Test
	void descriptionAllowsTabAndLineBreaksOnly() {
		assertThat(ProjectText.isAllowedDescription("1행\n\t2행\r\n3행")).isTrue();
		assertThat(ProjectText.isAllowedDescription("Doc​Flow ")).isTrue();
		assertThat(ProjectText.isAllowedDescription(null)).isTrue();
		assertThat(ProjectText.isAllowedDescription("설명\u0000")).isFalse();
		assertThat(ProjectText.isAllowedDescription("설명\u0007")).isFalse();
		assertThat(ProjectText.isAllowedDescription("설명\u007F")).isFalse();
	}

}
