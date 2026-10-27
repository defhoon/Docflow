package com.docflow.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 구성원 추가 요청 (SPEC-02 §5.4, §6.3). 이메일 검증 규칙은 SPEC-AUTH 로그인과 같다.
 * <p>
 * 이메일 원문은 응답과 로그에 남기지 않는다 (SPEC-02 결정 Q3, D6). {@link #toString()}도 이메일을 포함하지 않는다.
 * 정의되지 않은 필드({@code userId}, {@code role} 등)는 역직렬화 시 무시된다.
 */
public record AddMemberRequest(
		@NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.") String email) {

	public AddMemberRequest {
		email = (email != null) ? email.strip() : null;
	}

	@Override
	public String toString() {
		return "AddMemberRequest[email=****]";
	}

}
