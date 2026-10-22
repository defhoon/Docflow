package com.docflow.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.dto.AuthUserResponse;
import com.docflow.auth.dto.LoginRequest;
import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.auth.service.AuthService;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;

/**
 * 인증 API. 로그아웃({@code POST /api/auth/logout})은 Spring Security Logout 필터가 처리한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	private final SecurityContextRepository securityContextRepository;

	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
		.getContextHolderStrategy();

	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public AuthController(AuthService authService, SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SecurityContextRepository securityContextRepository) {
		this.authService = authService;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.securityContextRepository = securityContextRepository;
	}

	/**
	 * CSRF 토큰은 지연 로딩되므로 여기서 토큰 값을 읽어 {@code XSRF-TOKEN} 쿠키가 기록되게 한다.
	 */
	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
		csrfToken.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		Authentication authentication = this.authService.authenticate(request.email(), request.password());

		// 세션 고정 방어(세션 ID 변경)와 CSRF 토큰 교체
		this.sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

		// 커스텀 로그인 처리에서는 SecurityContext가 자동 저장되지 않으므로 명시적으로 저장한다.
		SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		this.securityContextHolderStrategy.setContext(context);
		this.securityContextRepository.saveContext(context, httpRequest, httpResponse);

		AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
		return new AuthUserResponse(principal.getId(), principal.getEmail(), principal.getDisplayName());
	}

	/**
	 * DB에서 사용자를 다시 확인한다. 삭제·비활성화된 계정이면 세션을 무효화하고 401을 응답한다.
	 */
	@GetMapping("/me")
	public AuthUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal, Authentication authentication,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		return this.authService.findActiveUser(principal.getId()).orElseThrow(() -> {
			this.logoutHandler.logout(httpRequest, httpResponse, authentication);
			return new BusinessException(ErrorCode.AUTH_REQUIRED);
		});
	}

}
