package com.docflow.document.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.document.dto.CreateDocumentRequest;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.dto.DocumentSummaryResponse;
import com.docflow.document.dto.FinalizeDocumentRequest;
import com.docflow.document.dto.UpdateDocumentDescriptionRequest;
import com.docflow.document.service.DocumentFinalizeService;
import com.docflow.document.service.DocumentService;

/**
 * 문서 API (SPEC-03 §6). 현재 사용자는 세션의 {@link AuthenticatedUser}에서만 얻는다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {

	private final DocumentService documentService;

	private final DocumentFinalizeService documentFinalizeService;

	public DocumentController(DocumentService documentService, DocumentFinalizeService documentFinalizeService) {
		this.documentService = documentService;
		this.documentFinalizeService = documentFinalizeService;
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

	/** 문서 설명 변경 (SPEC-09 §6). ACTIVE 문서만, OWNER·MEMBER. 응답은 갱신된 문서 상세다. */
	@PatchMapping(path = "/{documentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public DocumentResponse updateDescription(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId,
			@Valid @RequestBody UpdateDocumentDescriptionRequest request) {
		return this.documentService.updateDescription(projectId, documentId, principal.getId(), request.description());
	}

	/** 최종 버전 확정 (SPEC-07 §6). 오너 전용이며 FINALIZED는 영구 상태다. */
	@PostMapping("/{documentId}/finalize")
	public DocumentResponse finalizeDocument(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId,
			@Valid @RequestBody FinalizeDocumentRequest request) {
		return this.documentFinalizeService.finalizeDocument(projectId, documentId, principal.getId(),
				request.versionNumber());
	}

}
