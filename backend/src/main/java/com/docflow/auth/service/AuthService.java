package com.docflow.auth.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.docflow.auth.dto.AuthUserResponse;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * 자격 증명 확인과 현재 사용자 조회. 세션·쿠키 처리는 Controller가 담당한다.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AuthenticationManager authenticationManager;

	private final UserAccountService userAccountService;

	public AuthService(AuthenticationManager authenticationManager, UserAccountService userAccountService) {
		this.authenticationManager = authenticationManager;
		this.userAccountService = userAccountService;
	}

	/**
	 * 이메일·비밀번호를 확인한다. 존재하지 않는 계정, 비밀번호 불일치, 비활성 계정은 모두
	 * {@link ErrorCode#AUTH_INVALID_CREDENTIALS}로 처리하여 계정 존재 여부를 드러내지 않는다.
	 */
	public Authentication authenticate(String email, String password) {
		try {
			return this.authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(User.normalizeEmail(email), password));
		}
		catch (AuthenticationException ex) {
			// 이메일, 비밀번호, 실패 사유는 기록하지 않는다.
			log.info("Login failed");
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}
	}

	/**
	 * 세션의 사용자를 DB에서 다시 조회한다. 삭제되었거나 비활성화된 계정이면 비어 있는 값을 반환한다.
	 */
	public Optional<AuthUserResponse> findActiveUser(Long userId) {
		return this.userAccountService.findActiveById(userId).map(AuthUserResponse::from);
	}

}
