package com.docflow.compare.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.compare.dto.CompareResponse;
import com.docflow.compare.service.CompareTextUnavailableException;
import com.docflow.compare.service.VersionCompareService;
import com.docflow.global.error.ErrorCode;
import com.docflow.global.error.ErrorResponse;

/** 버전 비교 API (SPEC-12 §3). */
@RestController
public class VersionCompareController {

	private final VersionCompareService service;

	public VersionCompareController(VersionCompareService service) {
		this.service = service;
	}

	@GetMapping("/api/projects/{projectId}/documents/{documentId}/compare")
	public CompareResponse compare(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long projectId,
			@PathVariable Long documentId, @RequestParam int base, @RequestParam int target) {
		return this.service.compare(projectId, documentId, base, target, principal.getId());
	}

	/** 텍스트를 읽을 수 없는 버전: 어느 쪽인지와 추출 상태({@code NO_TEXT} 등)를 함께 알린다. */
	@ExceptionHandler(CompareTextUnavailableException.class)
	ResponseEntity<ErrorResponse> handleTextUnavailable(CompareTextUnavailableException ex) {
		ErrorCode code = ErrorCode.COMPARE_TEXT_UNAVAILABLE;
		return ResponseEntity.status(code.status())
			.body(ErrorResponse.of(code, List.of(new ErrorResponse.FieldError(ex.side(), ex.status().name()))));
	}

}
