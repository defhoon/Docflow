package com.docflow.document.controller;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.document.dto.CreateDocumentVersionRequest;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.service.DocumentVersionService;

/**
 * 문서 버전 API (SPEC-04 §6, 결정 OQ-5). 현재 사용자는 세션의 {@link AuthenticatedUser}에서만 얻는다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}/versions")
public class DocumentVersionController {

	private final DocumentVersionService documentVersionService;

	public DocumentVersionController(DocumentVersionService documentVersionService) {
		this.documentVersionService = documentVersionService;
	}

	/** 새 버전 등록. {@code Location}은 문서 상세를 가리킨다(버전 단건 조회는 SPEC-05 범위). */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DocumentResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId,
			@Valid @ModelAttribute CreateDocumentVersionRequest request) {
		DocumentResponse updated = this.documentVersionService.create(projectId, documentId, principal.getId(),
				request.file());
		return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/documents/" + documentId))
			.body(updated);
	}

}
