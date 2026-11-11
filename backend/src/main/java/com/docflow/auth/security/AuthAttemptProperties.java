package com.docflow.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 시도 제한 설정 (#26). 로그인은 이메일별, 가입은 이메일별 실패와 인스턴스 전체 실패를 함께 제한한다.
 */
@ConfigurationProperties("docflow.auth.attempts")
public record AuthAttemptProperties(@DefaultValue("5") int loginMaxFailures,
		@DefaultValue("15m") Duration loginWindow, @DefaultValue("5") int signupMaxFailures,
		@DefaultValue("15m") Duration signupWindow, @DefaultValue("100") int signupGlobalMaxFailures,
		@DefaultValue("10000") int maxTrackedKeys) {
}
