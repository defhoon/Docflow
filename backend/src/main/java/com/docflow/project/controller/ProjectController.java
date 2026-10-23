package com.docflow.project.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.project.dto.CreateProjectRequest;
import com.docflow.project.dto.ProjectResponse;
import com.docflow.project.dto.ProjectSummaryResponse;
import com.docflow.project.service.ProjectService;

/**
 * 프로젝트 API (SPEC-01 §6). 현재 사용자는 세션의 {@link AuthenticatedUser}에서만 얻는다.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody CreateProjectRequest request) {
		ProjectResponse created = this.projectService.create(principal.getId(), request);
		return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
	}

	@GetMapping
	public List<ProjectSummaryResponse> findMine(@AuthenticationPrincipal AuthenticatedUser principal) {
		return this.projectService.findMine(principal.getId());
	}

	@GetMapping("/{projectId}")
	public ProjectResponse getProject(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId) {
		return this.projectService.getProject(projectId, principal.getId());
	}

}
