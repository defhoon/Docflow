package com.docflow.document.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.docflow.document.service.VersionFile;

/**
 * SPEC-05 §6.4 다운로드 응답 헤더 (AC-VDL-04): {@code attachment}, ASCII 대체 이름과 RFC 5987 {@code filename*}, 서버 판별
 * Content Type, Content-Length, CSP.
 */
class DocumentFileResponseTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
			"요구사항 v2.docx|attachment; filename=\"_________ v2.docx\"; filename*=UTF-8''%EC%9A%94%EA%B5%AC%EC%82%AC%ED%95%AD%20v2.docx",
			"a\"b;c.pdf|attachment; filename=\"a\\\"b;c.pdf\"; filename*=UTF-8''a%22b%3Bc.pdf",
			"100% done.xlsx|attachment; filename=\"100% done.xlsx\"; filename*=UTF-8''100%25%20done.xlsx",
			"😀 x.pptx|attachment; filename=\"_ x.pptx\"; filename*=UTF-8''%F0%9F%98%80%20x.pptx" })
	void encodesFilenameForAttachment(String filename, String expected) {
		ResponseEntity<Resource> response = DocumentVersionController
			.fileResponse(new VersionFile(filename, "application/pdf", 3, new ByteArrayInputStream(new byte[3])));

		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo(expected);
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).doesNotContain("\r", "\n");
	}

	@ParameterizedTest
	@CsvSource({ "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" })
	void setsTypeLengthAndSandboxPolicy(String contentType) {
		ResponseEntity<Resource> response = DocumentVersionController
			.fileResponse(new VersionFile("a.pdf", contentType, 1234, new ByteArrayInputStream(new byte[0])));

		assertThat(response.getHeaders().getContentType()).hasToString(contentType);
		assertThat(response.getHeaders().getContentLength()).isEqualTo(1234);
		assertThat(response.getHeaders().getFirst("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
	}

}
