package com.docflow.document.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 최종 버전 확정 요청 (SPEC-07 §6). 문서 안의 버전 번호로 선택한다. */
public record FinalizeDocumentRequest(
		@NotNull(message = "최종 버전을 선택해 주세요.") @Min(value = 1, message = "최종 버전을 선택해 주세요.") Integer versionNumber) {
}
