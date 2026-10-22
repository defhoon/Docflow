package com.docflow.auth.dto;

import com.docflow.user.domain.User;

/**
 * 인증된 사용자 응답. 비밀번호 해시 등 내부 필드는 포함하지 않는다.
 */
public record AuthUserResponse(Long id, String email, String displayName) {

	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName());
	}

}
