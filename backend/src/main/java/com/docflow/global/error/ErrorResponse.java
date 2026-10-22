package com.docflow.global.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 오류 응답. 스택 트레이스, 예외 메시지, 거부된 입력값은 포함하지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.message(), null);
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
		return new ErrorResponse(errorCode.name(), errorCode.message(), fieldErrors);
	}

	public record FieldError(String field, String message) {
	}

}
