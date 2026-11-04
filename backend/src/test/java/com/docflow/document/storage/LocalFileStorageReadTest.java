package com.docflow.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPEC-05 §7.4 저장소 읽기: 저장된 파일을 그대로 읽고, 없는 파일은 {@link NoSuchFileException}, 루트 밖 경로는 거부한다.
 */
class LocalFileStorageReadTest {

	@TempDir
	private Path root;

	@Test
	void readsStoredFileAndReportsSize() throws IOException {
		LocalFileStorage storage = new LocalFileStorage(this.root.toString(), () -> "documents/read-me");
		Path temp = storage.createTempFile();
		Files.write(temp, new byte[] { 1, 2, 3, 4 });
		String key = storage.store(temp);

		assertThat(storage.size(key)).isEqualTo(4);
		try (InputStream in = storage.openForRead(key)) {
			assertThat(in.readAllBytes()).containsExactly(1, 2, 3, 4);
		}
		// 읽은 뒤 스트림이 닫혀 삭제할 수 있다 (Windows 파일 잠금)
		storage.delete(key);
		assertThat(this.root.resolve(key)).doesNotExist();
	}

	@Test
	void missingFileIsNoSuchFile() {
		LocalFileStorage storage = new LocalFileStorage(this.root.toString(), () -> "unused");

		assertThatThrownBy(() -> storage.size("documents/missing")).isInstanceOf(NoSuchFileException.class);
		assertThatThrownBy(() -> storage.openForRead("documents/missing")).isInstanceOf(NoSuchFileException.class);
	}

	@Test
	void rejectsKeysEscapingRoot() {
		LocalFileStorage storage = new LocalFileStorage(this.root.toString(), () -> "unused");

		assertThatThrownBy(() -> storage.openForRead("../outside")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.size("../outside")).isInstanceOf(IllegalArgumentException.class);
	}

}
