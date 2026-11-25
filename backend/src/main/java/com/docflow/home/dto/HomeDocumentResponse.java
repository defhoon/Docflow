package com.docflow.home.dto;

import java.time.Instant;

import com.docflow.document.domain.DocumentStatus;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.validation.DocumentFileType;

/**
 * 홈의 최근 문서 항목 (SPEC-18). 설명·작성자 이메일·저장 정보는 포함하지 않는다. {@code fileType}은 최신 버전 형식.
 */
public record HomeDocumentResponse(Long id, String title, DocumentStatus status, ProjectRef project,
		Integer latestVersionNumber, Integer finalVersionNumber, Instant updatedAt, DocumentResponse.Author latestUploader,
		String fileType) {

	public record ProjectRef(Long id, String name) {
	}

	/** JPQL 생성자 프로젝션용 ({@code DocumentRepository#findRecentAccessible}). */
	public HomeDocumentResponse(Long id, String title, DocumentStatus status, Long projectId, String projectName,
			Integer latestVersionNumber, Integer finalVersionNumber, Instant updatedAt, Long latestUploaderId,
			String latestUploaderDisplayName, String latestContentType) {
		this(id, title, status, new ProjectRef(projectId, projectName), latestVersionNumber, finalVersionNumber, updatedAt,
				new DocumentResponse.Author(latestUploaderId, latestUploaderDisplayName),
				DocumentFileType.fromContentType(latestContentType).map(Enum::name).orElse(null));
	}

}
