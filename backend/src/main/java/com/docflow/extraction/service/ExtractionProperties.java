package com.docflow.extraction.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import com.docflow.extraction.pdf.PdfExtractionLimits;

/**
 * 추출 설정 {@code docflow.extraction.*} (SPEC-10 §7). 업로드 파일 크기 상한(20MiB)은 업로드 단계에서 이미 적용된다.
 *
 * @param maxPages 페이지 수 상한 (기본 300)
 * @param maxWords 단어 수 상한 (기본 200,000)
 * @param timeout 문서당 처리 시간 상한 (기본 60초)
 * @param concurrency 동시 추출 수 (기본 2). 메모리 사용을 제한한다
 */
@ConfigurationProperties("docflow.extraction")
public record ExtractionProperties(Integer maxPages, Integer maxWords, Duration timeout, Integer concurrency) {

	@ConstructorBinding
	public ExtractionProperties {
		maxPages = (maxPages != null) ? maxPages : 300;
		maxWords = (maxWords != null) ? maxWords : 200_000;
		timeout = (timeout != null) ? timeout : Duration.ofSeconds(60);
		concurrency = (concurrency != null) ? concurrency : 2;
		if (concurrency < 1) {
			throw new IllegalArgumentException("docflow.extraction.concurrency must be >= 1");
		}
	}

	public PdfExtractionLimits limits() {
		return new PdfExtractionLimits(this.maxPages, this.maxWords, this.timeout);
	}

}
