package com.docflow.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** #37: IP를 알 때는 (IP, 이메일) 쌍으로 잠가 다른 곳의 공격자가 계정을 잠그지 못하게 하고, IP·계정 전체 상한을 둔다. */
class AuthAttemptGuardTest {

	private final AuthAttemptGuard guard = new AuthAttemptGuard(
			new AuthAttemptProperties(5, 8, 12, Duration.ofMinutes(15), 5, 3, Duration.ofMinutes(15), 100, 1000));

	private static final Optional<String> ATTACKER = Optional.of("203.0.113.66");

	private static final Optional<String> VICTIM = Optional.of("198.51.100.7");

	@Test
	void attackerFromAnotherIpCannotLockVictim() {
		for (int i = 0; i < 5; i++) {
			this.guard.loginFailed("victim@test.local", ATTACKER);
		}
		assertThatThrownBy(() -> this.guard.checkLogin("victim@test.local", ATTACKER))
			.isInstanceOf(TooManyAttemptsException.class);
		assertThatCode(() -> this.guard.checkLogin("victim@test.local", VICTIM)).doesNotThrowAnyException();
	}

	@Test
	void perIpCapStopsSprayingManyAccounts() {
		for (int i = 0; i < 8; i++) {
			this.guard.loginFailed("user" + i + "@test.local", ATTACKER);
		}
		assertThatThrownBy(() -> this.guard.checkLogin("someone@test.local", ATTACKER))
			.isInstanceOf(TooManyAttemptsException.class);
	}

	@Test
	void perAccountCapBoundsDistributedGuessing() {
		for (int i = 0; i < 12; i++) {
			this.guard.loginFailed("victim@test.local", Optional.of("203.0.113." + i));
		}
		assertThatThrownBy(() -> this.guard.checkLogin("victim@test.local", VICTIM))
			.isInstanceOf(TooManyAttemptsException.class);
	}

	@Test
	void unknownIpFallsBackToEmailLimit() {
		for (int i = 0; i < 5; i++) {
			this.guard.loginFailed("x@test.local", Optional.empty());
		}
		assertThatThrownBy(() -> this.guard.checkLogin("X@test.local", Optional.empty()))
			.isInstanceOf(TooManyAttemptsException.class);
	}

	@Test
	void signupPerIpLimit() {
		for (int i = 0; i < 3; i++) {
			this.guard.signupFailed("s" + i + "@test.local", ATTACKER);
		}
		assertThatThrownBy(() -> this.guard.checkSignup("new@test.local", ATTACKER))
			.isInstanceOf(TooManyAttemptsException.class);
		assertThatCode(() -> this.guard.checkSignup("new@test.local", VICTIM)).doesNotThrowAnyException();
	}

}
