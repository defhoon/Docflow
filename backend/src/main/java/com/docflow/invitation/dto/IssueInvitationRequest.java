package com.docflow.invitation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 초대코드 발급 요청 (#26). 유효 기간 1~336시간(기본 72), 메모 100자 이하(받는 사람 표시용). */
public record IssueInvitationRequest(
		@Min(value = 1, message = "유효 기간은 1~336시간입니다.") @Max(value = 336, message = "유효 기간은 1~336시간입니다.") Integer expiresInHours,
		@Size(max = 100, message = "메모는 100자 이하로 입력해 주세요.") String note) {
}
