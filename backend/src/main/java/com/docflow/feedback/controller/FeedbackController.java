package com.docflow.feedback.controller;

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
import com.docflow.feedback.dto.CreateFeedbackRequest;
import com.docflow.feedback.dto.FeedbackResponse;
import com.docflow.feedback.service.FeedbackService;

/** 버전별 피드백 API (SPEC-08 §6). */
@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}/versions/{versionNumber}/feedbacks")
public class FeedbackController {

	private final FeedbackService feedbackService;

	public FeedbackController(FeedbackService feedbackService) {
		this.feedbackService = feedbackService;
	}

	@PostMapping
	public ResponseEntity<FeedbackResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId, @PathVariable int versionNumber,
			@Valid @RequestBody CreateFeedbackRequest request) {
		FeedbackResponse created = this.feedbackService.create(projectId, documentId, versionNumber, principal.getId(),
				request.content());
		return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/documents/" + documentId
				+ "/versions/" + versionNumber + "/feedbacks/" + created.id()))
			.body(created);
	}

	@GetMapping
	public List<FeedbackResponse> findByVersion(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId, @PathVariable int versionNumber) {
		return this.feedbackService.findByVersion(projectId, documentId, versionNumber, principal.getId());
	}

}
