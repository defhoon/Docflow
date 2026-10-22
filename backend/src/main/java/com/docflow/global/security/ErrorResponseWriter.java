package com.docflow.global.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.docflow.global.error.ErrorCode;
import com.docflow.global.error.ErrorResponse;

/**
 * Security 필터 단계(Controller 밖)에서 {@link ErrorResponse}를 JSON으로 기록한다.
 */
@Component
public class ErrorResponseWriter {

	private final JsonMapper jsonMapper;

	public ErrorResponseWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(this.jsonMapper.writeValueAsString(ErrorResponse.of(errorCode)));
	}

}
