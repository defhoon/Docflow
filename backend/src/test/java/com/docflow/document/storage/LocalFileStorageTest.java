package com.docflow.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPEC-03 §5.4 파일 저장소: {@code CREATE_NEW} 배타적 생성, 소유권 기준 정리, 경로 이탈 방지 (AC-DOC-11, 28).
 */
class LocalFileStorageTest {

	@TempDir
	private Path root;

	private final Deque<String> keys = new ArrayDeque<>();

	private LocalFileStorage storage() {
		return new LocalFileStorage(this.root.toString(), this.keys::pop);
	}

	@Test
	void storesTempFileContentUnderGeneratedKeyAndKeepsTempForCaller() throws IOException {
		LocalFileStorage storage = storage();
		Path temp = storage.createTempFile();
		Files.writeString(temp, "content");
		this.keys.push("documents/abc");

		String key = storage.store(temp);

		assertThat(key).isEqualTo("documents/abc");
		assertThat(this.root.resolve("documents/abc")).hasContent("content");
		assertThat(temp.getParent()).isEqualTo(this.root.resolve("tmp"));
		storage.deleteTemp(temp);
		assertThat(temp).doesNotExist();
	}

	@Test
	void existingKeyIsNeverOverwrittenOrDeleted() throws IOException {
		Path existing = this.root.resolve("documents/taken");
		Files.createDirectories(existing.getParent());
		Files.writeString(existing, "original");
		LocalFileStorage storage = storage();
		Path temp = storage.createTempFile();
		Files.writeString(temp, "attacker");
		this.keys.push("documents/taken");

		assertThatThrownBy(() -> storage.store(temp)).isInstanceOf(StorageKeyConflictException.class)
			.isInstanceOf(FileAlreadyExistsException.class);

		assertThat(existing).hasContent("original");
	}

	@Test
	void deletesOnlyFileItCreatedWhenCopyFails() throws IOException {
		LocalFileStorage storage = storage();
		Path missingTemp = this.root.resolve("tmp/missing.tmp");
		this.keys.push("documents/created");

		assertThatThrownBy(() -> storage.store(missingTemp)).isInstanceOf(NoSuchFileException.class);

		assertThat(this.root.resolve("documents/created")).doesNotExist();
	}

	@Test
	void concurrentStoresToSameKeyCreateExactlyOneFile() throws Exception {
		Path tempA = storage().createTempFile();
		Path tempB = storage().createTempFile();
		Files.writeString(tempA, "A");
		Files.writeString(tempB, "B");
		LocalFileStorage storage = new LocalFileStorage(this.root.toString(), () -> "documents/race");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<String>> tasks = List.of(() -> {
				start.await();
				return storage.store(tempA);
			}, () -> {
				start.await();
				return storage.store(tempB);
			});
			List<Future<String>> futures = tasks.stream().map(executor::submit).toList();
			start.countDown();
			int succeeded = 0;
			int conflicts = 0;
			for (Future<String> future : futures) {
				try {
					future.get();
					succeeded++;
				}
				catch (java.util.concurrent.ExecutionException ex) {
					assertThat(ex.getCause()).isInstanceOf(StorageKeyConflictException.class);
					conflicts++;
				}
			}
			assertThat(succeeded).isEqualTo(1);
			assertThat(conflicts).isEqualTo(1);
			assertThat(Files.readString(this.root.resolve("documents/race"), StandardCharsets.UTF_8)).isIn("A", "B");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void deleteRemovesStoredFileAndIgnoresMissing() throws IOException {
		LocalFileStorage storage = storage();
		Path temp = storage.createTempFile();
		Files.writeString(temp, "x");
		this.keys.push("documents/d");
		String key = storage.store(temp);

		storage.delete(key);
		storage.delete(key);

		assertThat(this.root.resolve(key)).doesNotExist();
	}

	@Test
	void rejectsKeysEscapingRoot() {
		LocalFileStorage storage = storage();
		assertThatThrownBy(() -> storage.resolve("../outside")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.resolve("documents/../../outside"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.resolve(".")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.resolve("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deleteTempRejectsPathOutsideTempDirectory() throws IOException {
		LocalFileStorage storage = storage();
		Path stored = Files.writeString(this.root.resolve("keep"), "keep");
		assertThatThrownBy(() -> storage.deleteTemp(stored)).isInstanceOf(IllegalArgumentException.class);
		assertThat(stored).exists();
	}

	@Test
	void rejectsBlankRoot() {
		assertThatThrownBy(() -> new LocalFileStorage(" ", () -> "k")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void uuidGeneratorProducesDistinctKeysWithoutExtension() {
		UuidStorageKeyGenerator generator = new UuidStorageKeyGenerator();
		String a = generator.newKey();
		assertThat(a).matches("documents/[0-9a-f-]{36}");
		assertThat(generator.newKey()).isNotEqualTo(a);
	}

}
