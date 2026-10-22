package com.docflow.global.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import com.docflow.global.error.ErrorCode;

/**
 * 거부된 요청에 {@code 403} JSON을 응답한다. CSRF 검증 실패는 {@code CSRF_INVALID}로 구분하여
 * Frontend가 토큰 갱신 후 재시도할 수 있게 한다.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final ErrorResponseWriter errorResponseWriter;

	public RestAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ErrorCode errorCode = (accessDeniedException instanceof CsrfException) ? ErrorCode.CSRF_INVALID
				: ErrorCode.ACCESS_DENIED;
		this.errorResponseWriter.write(response, errorCode);
	}

}
