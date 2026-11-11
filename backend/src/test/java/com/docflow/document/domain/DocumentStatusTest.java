package com.docflow.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SPEC-07 문서 협업 상태: ACTIVE만 새 버전·협업 허용, FINALIZED는 거부 (SPEC-04 §7.2를 SPEC-07 FR-6으로 갱신).
 */
class DocumentStatusTest {

	@Test
	void activeAllowsCollaboration() {
		assertThat(DocumentStatus.ACTIVE.isCollaborationOpen()).isTrue();
		assertThat(DocumentStatus.ACTIVE.allowsNewVersion()).isTrue();
	}

	@Test
	void finalizedRejectsCollaboration() {
		assertThat(DocumentStatus.FINALIZED.isCollaborationOpen()).isFalse();
		assertThat(DocumentStatus.FINALIZED.allowsNewVersion()).isFalse();
	}

	@Test
	void coversEveryStatus() {
		assertThat(DocumentStatus.values()).containsExactly(DocumentStatus.ACTIVE, DocumentStatus.FINALIZED);
	}

}
