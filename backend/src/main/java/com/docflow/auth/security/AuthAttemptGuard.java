package com.docflow.auth.security;

import java.time.Clock;
import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 로그인·가입 시도 제한 (#26). 로그인: 이메일별 실패 {@code loginMaxFailures}회/창. 가입: 이메일별 실패와 인스턴스 전체 실패를
 * 제한해 초대코드 추측을 막는다(코드 자체도 128비트 난수).
 * <p>
 * IP 기준 제한은 두지 않는다: Backend는 Next.js 프록시 뒤 비공개 네트워크에 있어 원격 주소가 모두 프록시이고, 전달 헤더는
 * 위조될 수 있다. 계정 기준 잠금은 특정 계정을 일시적으로 잠글 수 있는 대가가 있으며 창 길이(기본 15분)로 제한한다.
 */
@Component
@EnableConfigurationProperties(AuthAttemptProperties.class)
public class AuthAttemptGuard {

	private static final String GLOBAL_KEY = "*";

	private final AttemptLimiter login;

	private final AttemptLimiter signup;

	private final AttemptLimiter signupGlobal;

	public AuthAttemptGuard(AuthAttemptProperties properties) {
		Clock clock = Clock.systemUTC();
		this.login = new AttemptLimiter(properties.loginMaxFailures(), properties.loginWindow(),
				properties.maxTrackedKeys(), clock);
		this.signup = new AttemptLimiter(properties.signupMaxFailures(), properties.signupWindow(),
				properties.maxTrackedKeys(), clock);
		this.signupGlobal = new AttemptLimiter(properties.signupGlobalMaxFailures(), properties.signupWindow(), 1,
				clock);
	}

	public void checkLogin(String email) {
		this.login.blockedFor(key(email)).ifPresent((wait) -> {
			throw new TooManyAttemptsException(wait);
		});
	}

	public void loginFailed(String email) {
		this.login.recordFailure(key(email));
	}

	public void loginSucceeded(String email) {
		this.login.reset(key(email));
	}

	public void checkSignup(String email) {
		this.signupGlobal.blockedFor(GLOBAL_KEY).or(() -> this.signup.blockedFor(key(email))).ifPresent((wait) -> {
			throw new TooManyAttemptsException(wait);
		});
	}

	public void signupFailed(String email) {
		this.signup.recordFailure(key(email));
		this.signupGlobal.recordFailure(GLOBAL_KEY);
	}

	private static String key(String email) {
		return (email == null) ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

}
