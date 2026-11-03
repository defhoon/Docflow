package com.docflow.global.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.docflow.document.validation.InvalidDocumentFileException;

/**
 * Controller 처리 중 발생한 예외를 {@link ErrorResponse}로 변환한다.
 * 응답과 로그에 요청 본문이나 입력값을 포함하지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 형 변환 실패 등 바인딩 오류의 기본 메시지에는 내부 타입 정보가 들어 있으므로 노출하지 않는다. */
	static final String BINDING_FAILURE_MESSAGE = "입력 형식이 올바르지 않습니다.";

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
		return toResponse(ex.errorCode());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> new ErrorResponse.FieldError(error.getField(),
					error.isBindingFailure() ? BINDING_FAILURE_MESSAGE : error.getDefaultMessage()))
			.toList();
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
			.body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors));
	}

	/** 업로드 파일 형식·구조·파일명 오류 (SPEC-03 결정 D6). */
	@ExceptionHandler(InvalidDocumentFileException.class)
	ResponseEntity<ErrorResponse> handleInvalidDocumentFile(InvalidDocumentFileException ex) {
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
			.body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
					List.of(new ErrorResponse.FieldError("file", ex.getMessage()))));
	}

	/** multipart 파일·요청 크기 상한 초과 (SPEC-03 결정 D3). */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		return toResponse(ErrorCode.FILE_TOO_LARGE);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
		return toResponse(ErrorCode.VALIDATION_FAILED);
	}

	/**
	 * 경로 변수·요청 파라미터 형식 오류 (예: 숫자가 아닌 {@code projectId}). 클라이언트 입력 오류이므로 ERROR 로그를
	 * 남기지 않는다.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return toResponse(ErrorCode.BAD_REQUEST);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
		return toResponse(ErrorCode.NOT_FOUND);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
		return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
		return toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		// Spring MVC가 4xx로 분류한 기타 요청 오류는 서버 오류로 기록하지 않는다.
		if (ex instanceof org.springframework.web.ErrorResponse errorResponse
				&& errorResponse.getStatusCode().is4xxClientError()) {
			return ResponseEntity.status(errorResponse.getStatusCode()).body(ErrorResponse.of(ErrorCode.BAD_REQUEST));
		}
		log.error("Unhandled exception: {}", ex.getClass().getName(), ex);
		return toResponse(ErrorCode.INTERNAL_ERROR);
	}

	private static ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
		return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
	}

}
