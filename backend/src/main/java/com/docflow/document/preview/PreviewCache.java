package com.docflow.document.preview;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 미리보기 PDF 캐시 (SPEC-05 §8.4, 결정 A3). 저장 루트의 {@code previews/cache/} 안에서만 동작하며 원본({@code documents/})은
 * 다루지 않는다.
 * <ul>
 * <li>키는 버전의 저장 키이고 파일 이름은 그 SHA-256이다. 버전 파일은 바뀌지 않으므로 무효화가 없다.</li>
 * <li>전체 크기가 상한을 넘으면 오래 사용하지 않은 것부터 상한의 90%까지 정리한다(LRU).</li>
 * <li>설정 기간 사용하지 않은 캐시는 주기적으로 정리한다.</li>
 * <li>전송 중(참조 수 &gt; 0)이거나 방금 등록한 캐시는 삭제하지 않는다. 삭제 실패(Windows 열린 파일 등)는 다음 정리에서 다시
 * 시도한다.</li>
 * </ul>
 * 색인은 메모리에 두고, 시작 시 디렉터리를 읽어 다시 만든다. 마지막 사용 시각은 파일 수정 시각에도 반영해 재시작 후 복원한다.
 */
public class PreviewCache {

	private static final Logger log = LoggerFactory.getLogger(PreviewCache.class);

	private static final Pattern CACHE_FILE = Pattern.compile("[0-9a-f]{64}\\.pdf");

	private final Path directory;

	private final long maxBytes;

	private final Clock clock;

	private final Map<String, Entry> entries = new HashMap<>();

	private long totalBytes;

	public PreviewCache(Path directory, long maxBytes, Clock clock) {
		this.directory = directory.toAbsolutePath().normalize();
		this.maxBytes = maxBytes;
		this.clock = clock;
	}

	/** 캐시 디렉터리를 만들고 색인을 다시 만든다. 완성되지 않은 임시 파일은 지운다. */
	public synchronized void rebuild() throws IOException {
		Files.createDirectories(this.directory);
		this.entries.clear();
		this.totalBytes = 0;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(this.directory)) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				if (Files.isRegularFile(file) && CACHE_FILE.matcher(name).matches()) {
					long size = Files.size(file);
					this.entries.put(name, new Entry(size, Files.getLastModifiedTime(file).toInstant()));
					this.totalBytes += size;
				}
				else {
					Files.deleteIfExists(file);
				}
			}
		}
	}

	/** 캐시가 있으면 사용 중으로 표시하고 읽기 스트림을 연다. 스트림을 닫으면 사용 표시가 풀린다. */
	public synchronized Optional<Lease> acquire(String storageKey) {
		String name = fileName(storageKey);
		Entry entry = this.entries.get(name);
		if (entry == null) {
			return Optional.empty();
		}
		Path file = this.directory.resolve(name);
		InputStream in;
		try {
			in = Files.newInputStream(file);
		}
		catch (IOException ex) {
			// 파일이 사라졌으면 색인에서 지우고 다시 변환하게 한다.
			remove(name, entry);
			return Optional.empty();
		}
		entry.references++;
		entry.lastAccess = this.clock.instant();
		touch(file, entry.lastAccess);
		return Optional.of(new Lease(entry.size, new FilterInputStream(in) {

			private boolean released;

			@Override
			public void close() throws IOException {
				try {
					super.close();
				}
				finally {
					if (!this.released) {
						this.released = true;
						release(name);
					}
				}
			}

		}));
	}

	/** 변환 결과를 캐시에 원자적으로 이동해 등록하고 용량을 정리한다. */
	public synchronized void put(String storageKey, Path producedPdf) throws IOException {
		String name = fileName(storageKey);
		Path target = this.directory.resolve(name);
		Files.createDirectories(this.directory);
		try {
			Files.move(producedPdf, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException ex) {
			Files.move(producedPdf, target, StandardCopyOption.REPLACE_EXISTING);
		}
		long size = Files.size(target);
		Instant now = this.clock.instant();
		Entry previous = this.entries.put(name, new Entry(size, now));
		if (previous != null) {
			this.totalBytes -= previous.size;
		}
		this.totalBytes += size;
		touch(target, now);
		enforceCapacity(name);
	}

	/** {@code maxIdle} 동안 사용하지 않은 캐시를 지운다. 사용 중인 캐시는 남긴다. */
	public synchronized int evictIdle(java.time.Duration maxIdle) {
		Instant threshold = this.clock.instant().minus(maxIdle);
		int removed = 0;
		for (Map.Entry<String, Entry> e : new ArrayList<>(this.entries.entrySet())) {
			if (e.getValue().references == 0 && e.getValue().lastAccess.isBefore(threshold)
					&& remove(e.getKey(), e.getValue())) {
				removed++;
			}
		}
		return removed;
	}

	public synchronized long totalBytes() {
		return this.totalBytes;
	}

	public synchronized boolean contains(String storageKey) {
		return this.entries.containsKey(fileName(storageKey));
	}

	Path directory() {
		return this.directory;
	}

	private synchronized void release(String name) {
		Entry entry = this.entries.get(name);
		if (entry != null && entry.references > 0) {
			entry.references--;
		}
	}

	private void enforceCapacity(String justAdded) {
		if (this.totalBytes <= this.maxBytes) {
			return;
		}
		long target = this.maxBytes * 9 / 10;
		List<Map.Entry<String, Entry>> candidates = new ArrayList<>(this.entries.entrySet());
		candidates.sort(Comparator.comparing((Map.Entry<String, Entry> e) -> e.getValue().lastAccess));
		for (Map.Entry<String, Entry> e : candidates) {
			if (this.totalBytes <= target) {
				break;
			}
			if (e.getKey().equals(justAdded) || e.getValue().references > 0) {
				continue;
			}
			remove(e.getKey(), e.getValue());
		}
		if (this.totalBytes > this.maxBytes) {
			log.warn("Preview cache over capacity: totalBytes={}, maxBytes={} (entries in use are kept)",
					this.totalBytes, this.maxBytes);
		}
	}

	private boolean remove(String name, Entry entry) {
		Path file = this.directory.resolve(name).normalize();
		if (!file.getParent().equals(this.directory) || !CACHE_FILE.matcher(name).matches()) {
			throw new IllegalStateException("refusing to delete outside preview cache");
		}
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			log.warn("Failed to delete preview cache file; will retry: {}", name, ex);
			return false;
		}
		this.entries.remove(name);
		this.totalBytes -= entry.size;
		return true;
	}

	private static void touch(Path file, Instant instant) {
		try {
			Files.setLastModifiedTime(file, FileTime.from(instant));
		}
		catch (IOException ex) {
			log.debug("Failed to update preview cache access time", ex);
		}
	}

	static String fileName(String storageKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(storageKey.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest) + ".pdf";
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** 사용 중인 캐시. {@link #content()}를 닫으면 사용 표시가 풀린다. */
	public record Lease(long sizeBytes, InputStream content) {
	}

	private static final class Entry {

		private final long size;

		private Instant lastAccess;

		private int references;

		private Entry(long size, Instant lastAccess) {
			this.size = size;
			this.lastAccess = lastAccess;
		}

	}

}
