package com.docflow.document.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로컬 파일시스템 저장소 (SPEC-03 §5.4, ADR-0001 Q2). 저장 루트는 {@code docflow.storage.dir}
 * ({@code DOCFLOW_STORAGE_DIR})이다.
 * <p>
 * 최종 파일은 {@link StandardOpenOption#CREATE_NEW}로 만든다. 존재 확인과 생성이 원자적이므로 같은 키의 파일이
 * 이미 있으면 동시 경쟁에서도 기존 파일을 덮어쓰지 않는다. {@code ATOMIC_MOVE}는 대상을 교체할 수 있어 사용하지
 * 않는다.
 */
@Component
public class LocalFileStorage implements FileStorage {

	private static final String TEMP_DIR = "tmp";

	private final Path root;

	private final StorageKeyGenerator keyGenerator;

	public LocalFileStorage(@Value("${docflow.storage.dir}") String rootDir, StorageKeyGenerator keyGenerator) {
		if (rootDir == null || rootDir.isBlank()) {
			// 빈 값이면 작업 디렉터리 전체가 저장 루트가 되므로 시작을 거부한다.
			throw new IllegalStateException("docflow.storage.dir must not be blank");
		}
		this.root = Path.of(rootDir).toAbsolutePath().normalize();
		this.keyGenerator = keyGenerator;
	}

	@Override
	public Path createTempFile() throws IOException {
		Path tempDir = Files.createDirectories(this.root.resolve(TEMP_DIR));
		return Files.createTempFile(tempDir, "upload-", ".tmp");
	}

	@Override
	public String store(Path validatedTempFile) throws IOException {
		String storageKey = this.keyGenerator.newKey();
		Path target = resolve(storageKey);
		Files.createDirectories(target.getParent());

		FileChannel out;
		try {
			out = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
		catch (FileAlreadyExistsException ex) {
			// 이 요청이 만든 파일이 아니므로 삭제하지 않는다.
			throw new StorageKeyConflictException(storageKey);
		}
		// 여기서부터 target은 이 호출이 생성한 파일이다.
		try (out; FileChannel in = FileChannel.open(validatedTempFile, StandardOpenOption.READ)) {
			long size = in.size();
			long position = 0;
			while (position < size) {
				long transferred = in.transferTo(position, size - position, out);
				if (transferred <= 0) {
					throw new IOException("Temp file shrank while copying");
				}
				position += transferred;
			}
			out.force(true);
		}
		catch (IOException | RuntimeException ex) {
			try {
				Files.deleteIfExists(target);
			}
			catch (IOException cleanupFailure) {
				ex.addSuppressed(cleanupFailure);
			}
			throw ex;
		}
		return storageKey;
	}

	@Override
	public void delete(String storageKey) throws IOException {
		Files.deleteIfExists(resolve(storageKey));
	}

	@Override
	public void deleteTemp(Path tempFile) throws IOException {
		Path normalized = tempFile.toAbsolutePath().normalize();
		if (!normalized.startsWith(this.root.resolve(TEMP_DIR))) {
			throw new IllegalArgumentException("temp file outside storage temp directory");
		}
		Files.deleteIfExists(normalized);
	}

	/** 저장 키 → 경로. 저장 키는 서버만 만들지만 저장 루트 이탈을 방어적으로 확인한다. */
	Path resolve(String storageKey) {
		Path path = this.root.resolve(storageKey).normalize();
		if (!path.startsWith(this.root) || path.equals(this.root)) {
			throw new IllegalArgumentException("storage key outside storage root");
		}
		return path;
	}

}
