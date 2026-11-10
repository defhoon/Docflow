package com.docflow.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영 프로파일의 쿠키 보안 강제 (#39). 세션 쿠키와 CSRF 쿠키의 Secure 속성은 같은 설정
 * ({@code server.servlet.session.cookie.secure})을 따르므로, 이 값이 false이면 운영 기동을 거부한다.
 * <p>
 * TLS는 Railway 엣지에서 끝나고 Backend는 내부 HTTP를 받지만, Secure 속성은 요청 스킴이 아니라 이 설정으로 붙으므로
 * 프록시 뒤에서도 유지된다. HTTPS 없이 로컬에서 prod 프로파일을 확인할 때만
 * {@code docflow.security.allow-insecure-cookies=true}로 명시적으로 허용하며, 허용 시 경고를 남긴다.
 */
@Component
@Profile("prod")
public class ProductionCookieGuard implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(ProductionCookieGuard.class);

	private final boolean secureCookie;

	private final boolean allowInsecure;

	public ProductionCookieGuard(@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie,
			@Value("${docflow.security.allow-insecure-cookies:false}") boolean allowInsecure) {
		this.secureCookie = secureCookie;
		this.allowInsecure = allowInsecure;
	}

	@Override
	public void afterPropertiesSet() {
		if (this.secureCookie) {
			return;
		}
		if (!this.allowInsecure) {
			throw new IllegalStateException("prod profile requires secure cookies: SESSION_COOKIE_SECURE must not be false "
					+ "(set DOCFLOW_ALLOW_INSECURE_COOKIES=true only for local HTTP checks)");
		}
		log.warn("prod profile is running with INSECURE cookies (DOCFLOW_ALLOW_INSECURE_COOKIES=true); never use this in production");
	}

}
