package com.docflow.document.dto;

import java.time.Instant;

import com.docflow.document.domain.DocumentStatus;

/**
 * 문서 목록 항목 (SPEC-03 §6.3). {@code description}은 SPEC-09 문서 설명(전체 값, 화면에서 줄여 표시)이다.
 */
public record DocumentSummaryResponse(Long id, String title, String description, DocumentStatus status,
		DocumentResponse.Author createdBy, Instant createdAt, Integer latestVersionNumber, Integer finalVersionNumber) {

	/** 목록 쿼리의 생성자 표현식용 ({@code DocumentRepository#findSummariesByProjectId}). */
	public DocumentSummaryResponse(Long id, String title, String description, DocumentStatus status, Long createdById,
			String createdByDisplayName, Instant createdAt, Integer latestVersionNumber, Integer finalVersionNumber) {
		this(id, title, description, status, new DocumentResponse.Author(createdById, createdByDisplayName), createdAt,
				latestVersionNumber, finalVersionNumber);
	}

}
