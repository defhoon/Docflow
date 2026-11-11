package com.docflow.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 로그인·가입 시도 제한 (#26, #37).
 * <p>
 * 클라이언트 IP를 신뢰할 수 있을 때({@link ClientIpResolver}): 로그인은 (IP, 이메일) 쌍 5회로 잠그므로 다른 곳의 공격자가
 * 특정 계정을 잠글 수 없다. 대신 IP당 전체 실패와 계정당 전체 실패(분산 대입 상한)를 넉넉하게 둔다.
 * IP를 알 수 없으면 이메일 기준 5회로 동작한다(특정 계정을 최대 창 길이만큼 잠글 수 있는 알려진 제약).
 * 가입은 이메일별·IP별·인스턴스 전체 실패를 제한한다. 단일 인스턴스 메모리 기반이다.
 */
@Component
@EnableConfigurationProperties(AuthAttemptProperties.class)
public class AuthAttemptGuard {

	private static final String GLOBAL_KEY = "*";

	private final AttemptLimiter loginPair;

	private final AttemptLimiter loginPerIp;

	private final AttemptLimiter loginPerEmail;

	private final AttemptLimiter signup;

	private final AttemptLimiter signupPerIp;

	private final AttemptLimiter signupGlobal;

	public AuthAttemptGuard(AuthAttemptProperties p) {
		Clock clock = Clock.systemUTC();
		int keys = p.maxTrackedKeys();
		this.loginPair = new AttemptLimiter(p.loginMaxFailures(), p.loginWindow(), keys, clock);
		this.loginPerIp = new AttemptLimiter(p.loginPerIpMaxFailures(), p.loginWindow(), keys, clock);
		this.loginPerEmail = new AttemptLimiter(p.loginPerEmailMaxFailures(), p.loginWindow(), keys, clock);
		this.signup = new AttemptLimiter(p.signupMaxFailures(), p.signupWindow(), keys, clock);
		this.signupPerIp = new AttemptLimiter(p.signupPerIpMaxFailures(), p.signupWindow(), keys, clock);
		this.signupGlobal = new AttemptLimiter(p.signupGlobalMaxFailures(), p.signupWindow(), 1, clock);
	}

	public void checkLogin(String email, Optional<String> clientIp) {
		String account = key(email);
		if (clientIp.isEmpty()) {
			throwIfBlocked(this.loginPair.blockedFor(account));
			return;
		}
		String ip = clientIp.get();
		throwIfBlocked(first(this.loginPair.blockedFor(ip + "|" + account), this.loginPerIp.blockedFor(ip),
				this.loginPerEmail.blockedFor(account)));
	}

	public void loginFailed(String email, Optional<String> clientIp) {
		String account = key(email);
		if (clientIp.isEmpty()) {
			this.loginPair.recordFailure(account);
			return;
		}
		String ip = clientIp.get();
		this.loginPair.recordFailure(ip + "|" + account);
		this.loginPerIp.recordFailure(ip);
		this.loginPerEmail.recordFailure(account);
	}

	/** 성공한 경로(쌍 또는 이메일)의 실패만 초기화한다. IP·계정 전체 상한은 창이 지나야 풀린다. */
	public void loginSucceeded(String email, Optional<String> clientIp) {
		String account = key(email);
		this.loginPair.reset(clientIp.map((ip) -> ip + "|" + account).orElse(account));
	}

	public void checkSignup(String email, Optional<String> clientIp) {
		throwIfBlocked(first(this.signupGlobal.blockedFor(GLOBAL_KEY), this.signup.blockedFor(key(email)),
				clientIp.flatMap(this.signupPerIp::blockedFor)));
	}

	public void signupFailed(String email, Optional<String> clientIp) {
		this.signup.recordFailure(key(email));
		this.signupGlobal.recordFailure(GLOBAL_KEY);
		clientIp.ifPresent(this.signupPerIp::recordFailure);
	}

	@SafeVarargs
	private static Optional<Duration> first(Optional<Duration>... candidates) {
		return List.of(candidates).stream().flatMap(Optional::stream).max(Duration::compareTo);
	}

	private static void throwIfBlocked(Optional<Duration> wait) {
		wait.ifPresent((duration) -> {
			throw new TooManyAttemptsException(duration);
		});
	}

	private static String key(String email) {
		return (email == null) ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

}
