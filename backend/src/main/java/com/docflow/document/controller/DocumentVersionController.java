package com.docflow.document.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.document.dto.CreateDocumentVersionRequest;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.dto.DocumentVersionResponse;
import com.docflow.document.preview.DocumentPreviewService;
import com.docflow.document.preview.PreviewBusyException;
import com.docflow.document.service.DocumentVersionQueryService;
import com.docflow.document.service.DocumentVersionService;
import com.docflow.document.service.VersionFile;
import com.docflow.global.error.ErrorResponse;

/**
 * 문서 버전 API: 새 버전 등록(SPEC-04 §6, 결정 OQ-5), 버전 히스토리·원본 다운로드(SPEC-05 §6). 현재 사용자는 세션의
 * {@link AuthenticatedUser}에서만 얻는다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}/versions")
public class DocumentVersionController {

	/** 파일 응답이 직접 열려도 스크립트·플러그인이 실행되지 않게 한다 (SPEC-05 §6.4, 심층 방어). */
	static final String FILE_CONTENT_SECURITY_POLICY = "default-src 'none'; sandbox";

	/** 변환 중 폴링 간격(초) (SPEC-05 §6.5). */
	static final String PREVIEW_RETRY_AFTER_SECONDS = "2";

	private final DocumentVersionService documentVersionService;

	private final DocumentVersionQueryService documentVersionQueryService;

	private final DocumentPreviewService documentPreviewService;

	public DocumentVersionController(DocumentVersionService documentVersionService,
			DocumentVersionQueryService documentVersionQueryService, DocumentPreviewService documentPreviewService) {
		this.documentVersionService = documentVersionService;
		this.documentVersionQueryService = documentVersionQueryService;
		this.documentPreviewService = documentPreviewService;
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

	/** 버전 히스토리 (SPEC-05 §6.3). */
	@GetMapping
	public List<DocumentVersionResponse> findVersions(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId) {
		return this.documentVersionQueryService.findVersions(projectId, documentId, principal.getId());
	}

	/** 원본 다운로드 (SPEC-05 §6.4). 항상 {@code attachment}이며 고정 버퍼로 스트리밍한다. */
	@GetMapping("/{versionNumber}/download")
	public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long projectId, @PathVariable Long documentId, @PathVariable int versionNumber) {
		return fileResponse(
				this.documentVersionQueryService.openOriginal(projectId, documentId, versionNumber, principal.getId()));
	}

	/**
	 * 미리보기 (SPEC-05 §6.5). 준비된 PDF는 {@code 200}, 변환 대기·진행 중이면 {@code 202} + {@code Retry-After}.
	 */
	@GetMapping("/{versionNumber}/preview")
	public ResponseEntity<?> preview(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long projectId,
			@PathVariable Long documentId, @PathVariable int versionNumber) {
		return switch (this.documentPreviewService.preview(projectId, documentId, versionNumber, principal.getId())) {
			case DocumentPreviewService.Ready ready -> fileResponse(ready.file());
			case DocumentPreviewService.Pending pending -> ResponseEntity.accepted()
				.header(HttpHeaders.RETRY_AFTER, PREVIEW_RETRY_AFTER_SECONDS)
				.body(Map.of("status", "PENDING"));
		};
	}

	/** 변환 대기열이 가득 참: {@code 503} + {@code Retry-After}. */
	@ExceptionHandler(PreviewBusyException.class)
	ResponseEntity<ErrorResponse> handlePreviewBusy(PreviewBusyException ex) {
		return ResponseEntity.status(ex.errorCode().status())
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(PreviewBusyException.RETRY_AFTER_SECONDS))
			.body(ErrorResponse.of(ex.errorCode()));
	}

	/**
	 * 파일 응답. 미리 지정한 {@code Content-Type}을 쓰므로 요청 {@code Accept}와 협상하지 않는다. {@code nosniff}·
	 * {@code no-store}·{@code X-Frame-Options}는 [기존] Spring Security 기본 헤더가 붙인다.
	 */
	static ResponseEntity<Resource> fileResponse(VersionFile file) {
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			.contentLength(file.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(file.downloadFilename(), StandardCharsets.UTF_8).build().toString())
			.header("Content-Security-Policy", FILE_CONTENT_SECURITY_POLICY)
			.body(new InputStreamResource(file.content()));
	}

}
