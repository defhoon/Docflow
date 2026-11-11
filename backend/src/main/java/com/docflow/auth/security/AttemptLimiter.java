package com.docflow.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 키별 실패 횟수 제한 (SPEC-AUTH 확장 #26). 창(window) 안에서 실패가 {@code maxFailures}회에 도달하면 가장 오래된 실패가 창을
 * 벗어날 때까지 차단한다. 메모리 사용량은 {@code maxKeys}로 제한하며 가득 차면 가장 오래 사용하지 않은 키를 버린다.
 * <p>
 * 단일 인스턴스 메모리 기반이다. 여러 인스턴스로 확장하면 공유 저장소가 필요하다(배포 ADR 참고).
 */
public final class AttemptLimiter {

	private final int maxFailures;

	private final Duration window;

	private final int maxKeys;

	private final Clock clock;

	private final Map<String, Deque<Instant>> failures;

	public AttemptLimiter(int maxFailures, Duration window, int maxKeys, Clock clock) {
		if (maxFailures < 1 || window.isNegative() || window.isZero() || maxKeys < 1) {
			throw new IllegalArgumentException("Invalid attempt limiter settings");
		}
		this.maxFailures = maxFailures;
		this.window = window;
		this.maxKeys = maxKeys;
		this.clock = clock;
		this.failures = new LinkedHashMap<>(16, 0.75f, true);
	}

	/** 차단 중이면 남은 시간(초 단위 올림, 최소 1초)을 반환한다. */
	public synchronized Optional<Duration> blockedFor(String key) {
		Deque<Instant> entries = prune(key);
		if (entries == null || entries.size() < this.maxFailures) {
			return Optional.empty();
		}
		Duration remaining = Duration.between(this.clock.instant(), entries.peekFirst().plus(this.window));
		long seconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
		return Optional.of(Duration.ofSeconds(seconds));
	}

	public synchronized void recordFailure(String key) {
		Deque<Instant> entries = prune(key);
		if (entries == null) {
			entries = new ArrayDeque<>();
			this.failures.put(key, entries);
			evictIfFull();
		}
		entries.addLast(this.clock.instant());
		while (entries.size() > this.maxFailures) {
			entries.removeFirst();
		}
	}

	public synchronized void reset(String key) {
		this.failures.remove(key);
	}

	synchronized int trackedKeys() {
		return this.failures.size();
	}

	private Deque<Instant> prune(String key) {
		Deque<Instant> entries = this.failures.get(key);
		if (entries == null) {
			return null;
		}
		Instant threshold = this.clock.instant().minus(this.window);
		while (!entries.isEmpty() && !entries.peekFirst().isAfter(threshold)) {
			entries.removeFirst();
		}
		if (entries.isEmpty()) {
			this.failures.remove(key);
			return null;
		}
		return entries;
	}

	private void evictIfFull() {
		Iterator<String> eldest = this.failures.keySet().iterator();
		while (this.failures.size() > this.maxKeys && eldest.hasNext()) {
			eldest.next();
			eldest.remove();
		}
	}

}
