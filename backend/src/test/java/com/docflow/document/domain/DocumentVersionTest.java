package com.docflow.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.docflow.project.domain.Project;
import com.docflow.user.domain.User;

/**
 * SPEC-04 §5.4 {@link DocumentVersion#next}, §5.3 {@link Document#markNewVersionAdded}.
 */
class DocumentVersionTest {

	private final User author = user(1L, "Author");

	private final User uploader = user(2L, "Uploader");

	private final Document document = Document.create(Project.create("P", null, this.author), "문서", "설명", this.author);

	private final StoredDocumentFile file = new StoredDocumentFile("v2.docx",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document", 42, "documents/key",
			"a".repeat(64));

	@Test
	void nextMapsFileAndUploader() {
		DocumentVersion version = DocumentVersion.next(this.document, 2, this.file, this.uploader);

		assertThat(version.getDocument()).isSameAs(this.document);
		assertThat(version.getVersionNumber()).isEqualTo(2);
		assertThat(version.getOriginalFilename()).isEqualTo("v2.docx");
		assertThat(version.getContentType()).isEqualTo(this.file.contentType());
		assertThat(version.getSizeBytes()).isEqualTo(42);
		assertThat(version.getStorageKey()).isEqualTo("documents/key");
		assertThat(version.getSha256()).isEqualTo("a".repeat(64));
		assertThat(version.getUploadedBy()).isSameAs(this.uploader);
	}

	@Test
	void nextRejectsFirstVersionNumberOrLower() {
		assertThatThrownBy(() -> DocumentVersion.next(this.document, 1, this.file, this.uploader))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DocumentVersion.next(this.document, 0, this.file, this.uploader))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void markNewVersionAddedChangesOnlyUpdatedAt() {
		this.document.markNewVersionAdded();

		assertThat(this.document.getUpdatedAt()).isNotNull();
		assertThat(this.document.getTitle()).isEqualTo("문서");
		assertThat(this.document.getDescription()).isEqualTo("설명");
		assertThat(this.document.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
		assertThat(this.document.getCreatedBy()).isSameAs(this.author);
	}

	private static User user(Long id, String name) {
		User user = User.create(name.toLowerCase() + "@test.local", "{noop}x", name);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
