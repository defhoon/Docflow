package com.docflow.extraction.pdf;

import java.time.Duration;

/**
 * 추출 상한 (SPEC-10 §7). 상한을 넘으면 부분 결과를 저장하지 않고 {@code TOO_LARGE}·{@code TIMEOUT}으로 끝낸다.
 *
 * @param maxPages 페이지 수 상한
 * @param maxWords 단어 수 상한
 * @param timeout 한 문서 처리 시간 상한
 */
public record PdfExtractionLimits(int maxPages, int maxWords, Duration timeout) {

	public PdfExtractionLimits {
		if (maxPages < 1 || maxWords < 1 || timeout == null || timeout.isNegative() || timeout.isZero()) {
			throw new IllegalArgumentException("Invalid extraction limits");
		}
	}

}
