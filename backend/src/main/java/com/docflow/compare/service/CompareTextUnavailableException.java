package com.docflow.compare.service;

import com.docflow.extraction.domain.ExtractionStatus;

/** 비교할 텍스트가 없는 버전 (422). {@code side}는 {@code base} 또는 {@code target}. */
public class CompareTextUnavailableException extends RuntimeException {

	private final String side;

	private final ExtractionStatus status;

	public CompareTextUnavailableException(String side, ExtractionStatus status) {
		super(side + ":" + status);
		this.side = side;
		this.status = status;
	}

	public String side() {
		return this.side;
	}

	public ExtractionStatus status() {
		return this.status;
	}

}
