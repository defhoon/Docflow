package com.docflow.document.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.document.dto.CreateDocumentRequest;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.dto.DocumentSummaryResponse;
import com.docflow.document.service.DocumentService;

/**
 * 문서 API (SPEC-03 §6). 현재 사용자는 세션의 {@link AuthenticatedUser}에서만 얻는다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DocumentResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @Valid @ModelAttribute CreateDocumentRequest request) {
		DocumentResponse created = this.documentService.create(projectId, principal.getId(), request);
		return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/documents/" + created.id()))
			.body(created);
	}

	@GetMapping
	public List<DocumentSummaryResponse> findDocuments(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId) {
		return this.documentService.findDocuments(projectId, principal.getId());
	}

	@GetMapping("/{documentId}")
	public DocumentResponse getDocument(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId) {
		return this.documentService.getDocument(projectId, documentId, principal.getId());
	}

}
