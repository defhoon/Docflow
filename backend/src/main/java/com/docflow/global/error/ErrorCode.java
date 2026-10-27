package com.docflow.global.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. Frontend는 HTTP 상태와 함께 {@code code}로 오류 종류를 구분한다.
 */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "요청을 처리할 수 없습니다. 페이지를 새로고침해 주세요."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
	PROJECT_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트 구성원을 찾을 수 없습니다."),
	PROJECT_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 프로젝트 구성원입니다."),
	PROJECT_OWNER_NOT_REMOVABLE(HttpStatus.BAD_REQUEST, "프로젝트 Owner는 제거할 수 없습니다."),
	DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일은 20MB 이하만 업로드할 수 있습니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 이메일로 가입한 사용자를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;

	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String message() {
		return this.message;
	}

}
