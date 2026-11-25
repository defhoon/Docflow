package com.docflow.extraction.service;

import com.docflow.extraction.domain.ExtractionStatus;
import com.docflow.extraction.pdf.ExtractedText;

/**
 * 버전 추출 결과 조회 값. {@code text}는 {@link ExtractionStatus#EXTRACTED}일 때만 있다.
 */
public record VersionText(Long versionId, ExtractionStatus status, int pageCount, ExtractedText text) {

	public boolean hasText() {
		return this.status.hasText();
	}

}
