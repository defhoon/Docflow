package com.docflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 초대코드 회원가입 요청 (#26). {@link #toString()}은 비밀번호와 초대코드를 마스킹한다. 비밀번호는 10~64자(bcrypt 72바이트
 * 제한은 서비스에서 확인).
 */
public record SignupRequest(@NotBlank(message = "초대코드를 입력해 주세요.") @Size(max = 64, message = "초대코드가 올바르지 않습니다.") String inviteCode,
		@NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.") String email,
		@NotBlank(message = "비밀번호를 입력해 주세요.") @Size(min = 10, max = 64, message = "비밀번호는 10~64자로 입력해 주세요.") String password,
		@NotBlank(message = "이름을 입력해 주세요.") @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.") String displayName) {

	public SignupRequest {
		email = (email != null) ? email.strip() : null;
		displayName = (displayName != null) ? displayName.strip() : null;
	}

	@Override
	public String toString() {
		return "SignupRequest[email=" + this.email + ", inviteCode=****, password=****]";
	}

}
