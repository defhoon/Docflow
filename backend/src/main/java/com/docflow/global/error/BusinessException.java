package com.docflow.global.error;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. {@link GlobalExceptionHandler}가 {@link ErrorResponse}로 변환한다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return this.errorCode;
	}

}
