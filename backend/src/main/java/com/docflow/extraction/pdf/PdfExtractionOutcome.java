package com.docflow.extraction.pdf;

import com.docflow.extraction.domain.ExtractionStatus;

/**
 * 추출 결과. {@code text}는 {@link ExtractionStatus#EXTRACTED}일 때만 있다.
 */
public record PdfExtractionOutcome(ExtractionStatus status, ExtractedText text, int pageCount) {

	public static PdfExtractionOutcome of(ExtractionStatus status, int pageCount) {
		return new PdfExtractionOutcome(status, null, pageCount);
	}

}
