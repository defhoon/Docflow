package com.docflow.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * SPEC-04 §7.2 새 버전 등록 허용 상태 (결정 V4, AC-VER-05).
 */
class DocumentStatusTest {

	@ParameterizedTest
	@EnumSource(value = DocumentStatus.class, names = { "DRAFT", "CHANGES_REQUESTED" })
	void allowsNewVersionInDraftAndChangesRequested(DocumentStatus status) {
		assertThat(status.allowsNewVersion()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = DocumentStatus.class, names = { "IN_REVIEW", "RE_REVIEW", "APPROVED" })
	void rejectsNewVersionInReviewAndApproved(DocumentStatus status) {
		assertThat(status.allowsNewVersion()).isFalse();
	}

	@Test
	void coversEveryStatus() {
		assertThat(DocumentStatus.values()).hasSize(5);
	}

}
