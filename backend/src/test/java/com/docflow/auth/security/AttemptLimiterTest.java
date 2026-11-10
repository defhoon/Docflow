package com.docflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** #26 시도 제한: 창 안의 실패 횟수, 창이 지나면 해제, 성공 시 초기화, 키 수 상한. */
class AttemptLimiterTest {

	private final MutableClock clock = new MutableClock();

	private final AttemptLimiter limiter = new AttemptLimiter(3, Duration.ofMinutes(15), 2, this.clock);

	@Test
	void blocksAfterMaxFailuresUntilOldestLeavesWindow() {
		this.limiter.recordFailure("a");
		this.clock.advance(Duration.ofMinutes(5));
		this.limiter.recordFailure("a");
		assertThat(this.limiter.blockedFor("a")).isEmpty();
		this.limiter.recordFailure("a");

		assertThat(this.limiter.blockedFor("a")).contains(Duration.ofMinutes(10));
		assertThat(this.limiter.blockedFor("b")).isEmpty();

		this.clock.advance(Duration.ofMinutes(10));
		assertThat(this.limiter.blockedFor("a")).isEmpty();
	}

	@Test
	void resetClearsFailures() {
		for (int i = 0; i < 3; i++) {
			this.limiter.recordFailure("a");
		}
		this.limiter.reset("a");
		assertThat(this.limiter.blockedFor("a")).isEmpty();
	}

	@Test
	void boundsTrackedKeys() {
		this.limiter.recordFailure("a");
		this.limiter.recordFailure("b");
		this.limiter.recordFailure("c");
		assertThat(this.limiter.trackedKeys()).isEqualTo(2);
	}

	static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-10T00:00:00Z");

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

}
