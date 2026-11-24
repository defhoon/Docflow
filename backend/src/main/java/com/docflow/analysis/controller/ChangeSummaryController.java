package com.docflow.analysis.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.analysis.domain.AnalysisType;
import com.docflow.analysis.dto.AnalysisResponse;
import com.docflow.analysis.service.DocumentAnalysisService;
import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;

/** 버전 조합 AI 변경 요약 API (SPEC-14 §3). 비용 통제를 위해 사용자가 요청할 때만 만든다. */
@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}/compare/summary")
public class ChangeSummaryController {

	private final DocumentAnalysisService service;

	public ChangeSummaryController(DocumentAnalysisService service) {
		this.service = service;
	}

	@GetMapping
	public AnalysisResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long projectId,
			@PathVariable Long documentId, @RequestParam int base, @RequestParam int target) {
		return this.service.get(target(projectId, documentId, base, target, principal), AnalysisType.CHANGE_SUMMARY);
	}

	@PostMapping
	public ResponseEntity<AnalysisResponse> request(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId, @RequestParam int base,
			@RequestParam int target) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(this.service.request(target(projectId, documentId, base, target, principal),
					AnalysisType.CHANGE_SUMMARY, principal.getId()));
	}

	private DocumentAnalysisService.Target target(Long projectId, Long documentId, int base, int target,
			AuthenticatedUser principal) {
		if (base == target) {
			throw new BusinessException(ErrorCode.COMPARE_SAME_VERSION);
		}
		return this.service.resolve(projectId, documentId, target, base, principal.getId());
	}

}
