package com.docflow.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.json.JsonMapper;

class RestAccessDeniedHandlerTest {

	private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(
			new ErrorResponseWriter(JsonMapper.builder().build()));

	@Test
	void csrfFailureIsReportedAsCsrfInvalid() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.handler.handle(new MockHttpServletRequest(), response, new MissingCsrfTokenException(null));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("\"code\":\"CSRF_INVALID\"");
	}

	@Test
	void otherAccessDeniedIsReportedAsAccessDenied() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("\"code\":\"ACCESS_DENIED\"").doesNotContain("denied\"");
	}

}
