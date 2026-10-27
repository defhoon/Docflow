package com.docflow.project.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.project.dto.AddMemberRequest;
import com.docflow.project.dto.MemberResponse;
import com.docflow.project.service.ProjectMemberService;

/**
 * 프로젝트 구성원 API (SPEC-02 §6). 권한 판단은 Service에서 한다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

	private final ProjectMemberService projectMemberService;

	public ProjectMemberController(ProjectMemberService projectMemberService) {
		this.projectMemberService = projectMemberService;
	}

	@GetMapping
	public List<MemberResponse> findMembers(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId) {
		return this.projectMemberService.findMembers(projectId, principal.getId());
	}

	/** 단건 구성원 조회 API가 없으므로 {@code Location} 헤더를 두지 않는다 (결정 Q14). */
	@PostMapping
	public ResponseEntity<MemberResponse> addMember(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @Valid @RequestBody AddMemberRequest request) {
		MemberResponse added = this.projectMemberService.addMember(projectId, principal.getId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(added);
	}

	@DeleteMapping("/{userId}")
	public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long userId) {
		this.projectMemberService.removeMember(projectId, principal.getId(), userId);
		return ResponseEntity.noContent().build();
	}

}
