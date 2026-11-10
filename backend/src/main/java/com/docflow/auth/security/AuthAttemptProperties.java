package com.docflow.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 시도 제한 설정 (#26, #37). 창(window) 안의 실패 횟수 상한.
 *
 * @param loginMaxFailures 로그인: 같은 (IP, 이메일) 실패 상한. IP를 알 수 없으면 이메일 기준
 * @param loginPerIpMaxFailures 로그인: 같은 IP의 모든 계정 실패 상한(여러 계정 대입 방지)
 * @param loginPerEmailMaxFailures 로그인: IP를 알 때 한 계정에 대한 전체 실패 상한(분산 대입 상한)
 * @param signupPerIpMaxFailures 가입: 같은 IP 실패 상한(IP를 알 때)
 */
@ConfigurationProperties("docflow.auth.attempts")
public record AuthAttemptProperties(@DefaultValue("5") int loginMaxFailures,
		@DefaultValue("20") int loginPerIpMaxFailures, @DefaultValue("50") int loginPerEmailMaxFailures,
		@DefaultValue("15m") Duration loginWindow, @DefaultValue("5") int signupMaxFailures,
		@DefaultValue("10") int signupPerIpMaxFailures, @DefaultValue("15m") Duration signupWindow,
		@DefaultValue("100") int signupGlobalMaxFailures, @DefaultValue("10000") int maxTrackedKeys) {
}
