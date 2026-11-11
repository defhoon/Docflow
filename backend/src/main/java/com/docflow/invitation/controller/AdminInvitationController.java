package com.docflow.invitation.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.invitation.dto.InvitationResponse;
import com.docflow.invitation.dto.IssueInvitationRequest;
import com.docflow.invitation.dto.IssuedInvitationResponse;
import com.docflow.invitation.service.InvitationService;

/** 관리자 초대코드 API (#26). 관리자 여부는 매 요청 DB에서 확인한다. */
@RestController
@RequestMapping("/api/admin/invitations")
public class AdminInvitationController {

	private final InvitationService invitationService;

	public AdminInvitationController(InvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@PostMapping
	public ResponseEntity<IssuedInvitationResponse> issue(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody IssueInvitationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.header("Cache-Control", "no-store")
			.body(this.invitationService.issue(principal.getId(), request.expiresInHours(), request.note()));
	}

	@GetMapping
	public List<InvitationResponse> findRecent(@AuthenticationPrincipal AuthenticatedUser principal) {
		return this.invitationService.findRecent(principal.getId());
	}

	@PostMapping("/{invitationId}/revoke")
	public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long invitationId) {
		this.invitationService.revoke(principal.getId(), invitationId);
		return ResponseEntity.noContent().build();
	}

}
