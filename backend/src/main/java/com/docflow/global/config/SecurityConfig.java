package com.docflow.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;

import com.docflow.global.security.RestAccessDeniedHandler;
import com.docflow.global.security.RestAuthenticationEntryPoint;

/**
 * Spring Security 세션 인증 구성 (SPEC-AUTH §6, ADR-0005).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	private static final String SESSION_COOKIE_NAME = "JSESSIONID";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository,
			RestAuthenticationEntryPoint authenticationEntryPoint, RestAccessDeniedHandler accessDeniedHandler)
			throws Exception {
		http.authorizeHttpRequests((authorize) -> authorize
			.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
			.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
			// Controller 밖에서 발생한 오류의 내부 포워드. 응답에는 상세 정보가 포함되지 않는다.
			.requestMatchers("/error").permitAll()
			.requestMatchers("/api/**").authenticated()
			.anyRequest().denyAll())
			// SPA용 CSRF: XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더. login·logout 포함 모든 변경 요청에 적용
			.csrf((csrf) -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.securityContext((context) -> context.securityContextRepository(securityContextRepository))
			.sessionManagement((session) -> session.sessionFixation().changeSessionId())
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			// 세션이 없어도 204. 세션이 있으면 무효화하고 SecurityContext·CSRF 토큰·세션 쿠키를 제거한다.
			.logout((logout) -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
				.deleteCookies(SESSION_COOKIE_NAME))
			.requestCache((cache) -> cache.disable())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(
			@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer((cookie) -> cookie.sameSite("Lax").secure(secureCookie).path("/"));
		return repository;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	/**
	 * 로그인 성공 시 호출: 세션 ID 변경(세션 고정 방어) 후 CSRF 토큰을 교체한다.
	 */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CookieCsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

}
