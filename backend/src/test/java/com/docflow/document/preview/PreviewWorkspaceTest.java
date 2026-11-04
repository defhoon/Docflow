package com.docflow.document.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPEC-05 §8.5 작업 디렉터리 삭제: Windows에서 LibreOffice가 만드는 읽기 전용 알려진 폴더(예: {@code Documents})가 있어도 지우고,
 * 작업 루트 밖과 심볼릭 링크 대상은 지우지 않는다.
 */
class PreviewWorkspaceTest {

	@TempDir
	private Path root;

	@Test
	void deletesTreeContainingReadOnlyKnownFolder() throws IOException {
		Path job = this.root.resolve("job");
		Path documents = Files.createDirectories(job.resolve("Documents"));
		Files.writeString(documents.resolve("desktop.ini"), "x");
		Files.createDirectories(job.resolve("profile/user"));
		markReadOnly(documents.resolve("desktop.ini"));
		markReadOnly(documents); // Windows 알려진 폴더와 같은 읽기 전용(DOS) 속성

		PreviewWorkspace.deleteRecursively(this.root, job);

		assertThat(job).doesNotExist();
		assertThat(this.root).exists();
	}

	@Test
	void deleteContentsKeepsRoot() throws IOException {
		Files.createDirectories(this.root.resolve("a/Documents"));
		markReadOnly(this.root.resolve("a/Documents"));
		Files.writeString(this.root.resolve("b.pdf"), "pdf");

		PreviewWorkspace.deleteContents(this.root);

		try (var children = Files.list(this.root)) {
			assertThat(children).isEmpty();
		}
	}

	@Test
	void missingTargetIsNoOpAndOutsideRootIsRejected() throws IOException {
		PreviewWorkspace.deleteRecursively(this.root, this.root.resolve("missing"));

		Path outside = Files.createTempDirectory("docflow-outside");
		assertThatThrownBy(() -> PreviewWorkspace.deleteRecursively(this.root.resolve("work"), outside))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(outside).exists();
		Files.delete(outside);
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	void doesNotFollowSymbolicLinks() throws IOException {
		Path outside = Files.createTempDirectory("docflow-outside");
		Path keep = Files.writeString(outside.resolve("keep.txt"), "keep");
		Path job = Files.createDirectories(this.root.resolve("job"));
		Files.createSymbolicLink(job.resolve("link"), outside);

		PreviewWorkspace.deleteRecursively(this.root, job);

		assertThat(job).doesNotExist();
		assertThat(keep).hasContent("keep");
		Files.delete(keep);
		Files.delete(outside);
	}

	/** DOS 속성을 지원하는 파일시스템(Windows)에서만 읽기 전용을 표시한다. 다른 파일시스템에서는 삭제 경로만 검증된다. */
	static void markReadOnly(Path path) throws IOException {
		DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class);
		if (view != null) {
			try {
				view.setReadOnly(true);
			}
			catch (UnsupportedOperationException | IOException ex) {
				// DOS 속성을 저장할 수 없는 파일시스템(예: 확장 속성이 없는 Linux)에서는 표시하지 않는다.
			}
		}
	}

}
