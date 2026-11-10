package com.docflow.audit.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.audit.dto.ActivityResponse;
import com.docflow.audit.service.AuditLogService;
import com.docflow.auth.security.AuthenticatedUser;

/** 활동 이력 조회 API (SPEC-06 §6). 기록 API는 없다(append-only, 서버 내부에서만 기록). */
@RestController
public class ActivityController {

	private final AuditLogService auditLogService;

	public ActivityController(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	@GetMapping("/api/activities")
	public List<ActivityResponse> findMine(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) Long beforeId) {
		return this.auditLogService.findMine(principal.getId(), limit, beforeId);
	}

	@GetMapping("/api/projects/{projectId}/activities")
	public List<ActivityResponse> findByProject(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Long beforeId) {
		return this.auditLogService.findByProject(projectId, principal.getId(), limit, beforeId);
	}

	@GetMapping("/api/projects/{projectId}/documents/{documentId}/activities")
	public List<ActivityResponse> findByDocument(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId, @RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Long beforeId) {
		return this.auditLogService.findByDocument(projectId, documentId, principal.getId(), limit, beforeId);
	}

}
