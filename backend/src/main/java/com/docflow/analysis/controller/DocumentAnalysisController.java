package com.docflow.analysis.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.analysis.domain.AnalysisType;
import com.docflow.analysis.dto.AnalysisResponse;
import com.docflow.analysis.service.DocumentAnalysisService;
import com.docflow.auth.security.AuthenticatedUser;

/** 버전 AI 요약 API (SPEC-11 §3). 요청은 비동기이며 {@code 202}와 현재 상태를 돌려준다. */
@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}/versions/{versionNumber}/summary")
public class DocumentAnalysisController {

	private final DocumentAnalysisService service;

	public DocumentAnalysisController(DocumentAnalysisService service) {
		this.service = service;
	}

	@GetMapping
	public AnalysisResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long projectId,
			@PathVariable Long documentId, @PathVariable int versionNumber) {
		return this.service.get(this.service.resolve(projectId, documentId, versionNumber, null, principal.getId()),
				AnalysisType.SUMMARY);
	}

	@PostMapping
	public ResponseEntity<AnalysisResponse> request(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId, @PathVariable int versionNumber) {
		DocumentAnalysisService.Target target = this.service.resolve(projectId, documentId, versionNumber, null,
				principal.getId());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(this.service.request(target, AnalysisType.SUMMARY, principal.getId()));
	}

}
