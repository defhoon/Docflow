package com.docflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청. {@link #toString()}은 비밀번호를 마스킹하여 로그 노출을 막는다.
 */
public record LoginRequest(
		@NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.") String email,
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(max = 128, message = "비밀번호는 128자 이하로 입력해 주세요.") String password) {

	/**
	 * 이메일 앞뒤 공백은 검증 전에 제거한다 (SPEC-AUTH §5.4). 대소문자 정규화는 인증 단계에서 수행한다.
	 */
	public LoginRequest {
		email = (email != null) ? email.strip() : null;
	}

	@Override
	public String toString() {
		return "LoginRequest[email=" + this.email + ", password=****]";
	}

}
