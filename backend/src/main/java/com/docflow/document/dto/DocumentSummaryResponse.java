package com.docflow.document.dto;

import java.time.Instant;

import com.docflow.document.domain.DocumentStatus;

/**
 * 문서 목록 항목 (SPEC-03 §6.3).
 */
public record DocumentSummaryResponse(Long id, String title, DocumentStatus status, DocumentResponse.Author createdBy,
		Instant createdAt, Integer latestVersionNumber) {

	/** 목록 쿼리의 생성자 표현식용 ({@code DocumentRepository#findSummariesByProjectId}). */
	public DocumentSummaryResponse(Long id, String title, DocumentStatus status, Long createdById,
			String createdByDisplayName, Instant createdAt, Integer latestVersionNumber) {
		this(id, title, status, new DocumentResponse.Author(createdById, createdByDisplayName), createdAt,
				latestVersionNumber);
	}

}
