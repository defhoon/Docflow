package com.docflow.document.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPEC-05 §8.4 미리보기 캐시 (AC-VDL-15): 용량 상한 LRU 정리, 미사용 정리, 사용 중 보호, 원본 경로 보호, 재시작 시 색인 재구성.
 */
class PreviewCacheTest {

	@TempDir
	private Path root;

	private MutableClock clock;

	private Path cacheDir;

	private Path originals;

	@BeforeEach
	void setUp() throws IOException {
		this.clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
		this.cacheDir = this.root.resolve("previews/cache");
		this.originals = Files.createDirectories(this.root.resolve("documents"));
	}

	@Test
	void evictsLeastRecentlyUsedUntilNinetyPercentWhenOverCapacity() throws IOException {
		PreviewCache cache = cache(1000);
		put(cache, "a", 300);
		this.clock.advance(Duration.ofMinutes(1));
		put(cache, "b", 300);
		this.clock.advance(Duration.ofMinutes(1));
		put(cache, "c", 300);
		this.clock.advance(Duration.ofMinutes(1));
		// a를 최근에 사용 → b가 가장 오래됨
		read(cache, "a");
		this.clock.advance(Duration.ofMinutes(1));

		put(cache, "d", 300); // 1200 > 1000 → 상한의 90%(900) 이하가 될 때까지 가장 오래된 b부터 정리

		assertThat(cache.contains("b")).isFalse();
		assertThat(cache.contains("a")).isTrue();
		assertThat(cache.contains("c")).isTrue();
		assertThat(cache.contains("d")).isTrue();
		assertThat(cache.totalBytes()).isEqualTo(900);
		assertThat(cacheFiles()).isEqualTo(3);
	}

	@Test
	void entriesInUseAndJustAddedAreNotEvicted() throws IOException {
		PreviewCache cache = cache(500);
		put(cache, "busy", 400);
		PreviewCache.Lease lease = cache.acquire("busy").orElseThrow();

		put(cache, "new", 400); // 800 > 500이지만 busy는 전송 중, new는 방금 등록

		assertThat(cache.contains("busy")).isTrue();
		assertThat(cache.contains("new")).isTrue();
		lease.content().close();
		this.clock.advance(Duration.ofSeconds(1));
		put(cache, "next", 100); // 이제 busy를 정리할 수 있다

		assertThat(cache.contains("busy")).isFalse();
	}

	@Test
	void evictsIdleEntriesButKeepsEntriesInUse() throws IOException {
		PreviewCache cache = cache(10_000);
		put(cache, "old", 10);
		put(cache, "old-in-use", 10);
		PreviewCache.Lease lease = cache.acquire("old-in-use").orElseThrow();
		this.clock.advance(Duration.ofDays(31));
		put(cache, "fresh", 10);

		int removed = cache.evictIdle(Duration.ofDays(30));

		assertThat(removed).isEqualTo(1);
		assertThat(cache.contains("old")).isFalse();
		assertThat(cache.contains("old-in-use")).isTrue();
		assertThat(cache.contains("fresh")).isTrue();
		lease.content().close();
	}

	@Test
	void accessUpdatesLastUseSoIdleCleanupKeepsRecentlyReadEntries() throws IOException {
		PreviewCache cache = cache(10_000);
		put(cache, "k", 10);
		this.clock.advance(Duration.ofDays(29));
		read(cache, "k");
		this.clock.advance(Duration.ofDays(29));

		assertThat(cache.evictIdle(Duration.ofDays(30))).isZero();
		assertThat(cache.contains("k")).isTrue();
	}

	@Test
	void rebuildRestoresIndexFromFilesAndDeletesStrayFiles() throws IOException {
		PreviewCache first = cache(10_000);
		put(first, "k1", 50);
		Files.writeString(this.cacheDir.resolve("partial.tmp"), "half");

		PreviewCache restarted = cache(10_000);

		assertThat(restarted.contains("k1")).isTrue();
		assertThat(restarted.totalBytes()).isEqualTo(50);
		assertThat(this.cacheDir.resolve("partial.tmp")).doesNotExist();
	}

	@Test
	void missingCacheFileIsTreatedAsMiss() throws IOException {
		PreviewCache cache = cache(10_000);
		put(cache, "gone", 10);
		Files.delete(this.cacheDir.resolve(PreviewCache.fileName("gone")));

		assertThat(cache.acquire("gone")).isEmpty();
		assertThat(cache.contains("gone")).isFalse();
		assertThat(cache.totalBytes()).isZero();
	}

	@Test
	void neverTouchesOriginalsAndUsesHashedNames() throws IOException {
		Path original = Files.writeString(this.originals.resolve("keep"), "original");
		PreviewCache cache = cache(100);
		put(cache, "documents/keep", 90);
		this.clock.advance(Duration.ofDays(400));
		put(cache, "documents/other", 90);
		cache.evictIdle(Duration.ofDays(1));

		assertThat(original).hasContent("original");
		try (Stream<Path> files = Files.list(this.cacheDir)) {
			assertThat(files.map((p) -> p.getFileName().toString())).allMatch((n) -> n.matches("[0-9a-f]{64}\\.pdf"));
		}
		assertThat(PreviewCache.fileName("documents/keep")).doesNotContain("documents").doesNotContain("/");
		assertThatThrownBy(() -> cache.put("x", this.root.resolve("does-not-exist.pdf"))).isInstanceOf(IOException.class);
	}

	private PreviewCache cache(long maxBytes) throws IOException {
		PreviewCache cache = new PreviewCache(this.cacheDir, maxBytes, this.clock);
		cache.rebuild();
		return cache;
	}

	private void put(PreviewCache cache, String key, int size) throws IOException {
		Path produced = Files.createTempFile(this.root, "out", ".pdf");
		Files.write(produced, new byte[size]);
		cache.put(key, produced);
	}

	private static void read(PreviewCache cache, String key) throws IOException {
		try (InputStream in = cache.acquire(key).orElseThrow().content()) {
			in.readAllBytes();
		}
	}

	private long cacheFiles() throws IOException {
		try (Stream<Path> files = Files.list(this.cacheDir)) {
			return files.count();
		}
	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

}
