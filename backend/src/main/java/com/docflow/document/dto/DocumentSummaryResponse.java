package com.docflow.document.dto;

import java.time.Instant;

import com.docflow.document.domain.DocumentStatus;
import com.docflow.document.validation.DocumentFileType;

/**
 * 문서 목록 항목 (SPEC-03 §6.3). {@code description}은 SPEC-09 문서 설명(전체 값, 화면에서 줄여 표시)이다.
 * <p>
 * SPEC-18: {@code updatedAt}(문서 변경 시각: 새 버전·설명 변경·최종 확정), {@code latestUploader}(최신 버전 업로더),
 * {@code fileType}(최신 버전 형식, {@code PDF}·{@code DOCX}·{@code XLSX}·{@code PPTX}). Content Type 원문은 포함하지 않는다.
 */
public record DocumentSummaryResponse(Long id, String title, String description, DocumentStatus status,
		DocumentResponse.Author createdBy, Instant createdAt, Integer latestVersionNumber, Integer finalVersionNumber,
		Instant updatedAt, DocumentResponse.Author latestUploader, String fileType) {

	/** 목록 쿼리의 생성자 표현식용 ({@code DocumentRepository#findSummariesByProjectId}). */
	public DocumentSummaryResponse(Long id, String title, String description, DocumentStatus status, Long createdById,
			String createdByDisplayName, Instant createdAt, Integer latestVersionNumber, Integer finalVersionNumber,
			Instant updatedAt, Long latestUploaderId, String latestUploaderDisplayName, String latestContentType) {
		this(id, title, description, status, new DocumentResponse.Author(createdById, createdByDisplayName), createdAt,
				latestVersionNumber, finalVersionNumber, updatedAt,
				new DocumentResponse.Author(latestUploaderId, latestUploaderDisplayName), fileType(latestContentType));
	}

	static String fileType(String contentType) {
		return DocumentFileType.fromContentType(contentType).map(Enum::name).orElse(null);
	}

}
