package com.docflow.analysis.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.analysis.dto.AiSettingsResponse;
import com.docflow.analysis.dto.UpdateAiSettingsRequest;
import com.docflow.analysis.service.AiSettingsService;
import com.docflow.auth.security.AuthenticatedUser;

/** 프로젝트 AI 설정 API (SPEC-11 §3). */
@RestController
@RequestMapping("/api/projects/{projectId}/ai-settings")
public class AiSettingsController {

	private final AiSettingsService service;

	public AiSettingsController(AiSettingsService service) {
		this.service = service;
	}

	@GetMapping
	public AiSettingsResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long projectId) {
		return this.service.get(projectId, principal.getId());
	}

	@PutMapping
	public AiSettingsResponse update(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long projectId,
			@Valid @RequestBody UpdateAiSettingsRequest request) {
		return this.service.update(projectId, principal.getId(), request.enabled(),
				Boolean.TRUE.equals(request.consent()));
	}

}
