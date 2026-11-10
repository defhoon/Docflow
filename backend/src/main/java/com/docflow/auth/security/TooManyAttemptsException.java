package com.docflow.auth.security;

import java.time.Duration;

/** 시도 횟수 제한 초과 (429, {@code Retry-After}). 어떤 계정·코드가 원인인지 응답에 드러내지 않는다. */
public class TooManyAttemptsException extends RuntimeException {

	private final Duration retryAfter;

	public TooManyAttemptsException(Duration retryAfter) {
		super("Too many attempts");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return this.retryAfter;
	}

}
