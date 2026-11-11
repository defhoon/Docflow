package com.docflow.feedback.dto;

import jakarta.validation.constraints.NotBlank;

/** 피드백 작성 요청 (SPEC-08 §6). 길이(앞뒤 공백 제거 후 2,000자)는 서비스가 판단한다. */
public record CreateFeedbackRequest(@NotBlank(message = "피드백 내용을 입력해 주세요.") String content) {
}
