package com.docflow.document.preview;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.unit.DataSize;

/**
 * 미리보기 설정 {@code docflow.preview.*} (SPEC-05 §8.3, ADR-0006). 값이 없으면 승인된 기본값을 쓴다.
 *
 * @param timeout 변환 시간 제한 (기본 60초)
 * @param concurrency 동시 변환 수 (기본 1)
 * @param queueCapacity 대기열 크기 (기본 10, 초과 시 503)
 * @param maxOutputBytes 결과 PDF 크기 상한 (기본 100MiB)
 * @param sofficePath LibreOffice 실행 파일 경로. 비어 있으면 PATH와 Windows 기본 설치 경로에서 찾는다
 * @param launcher 실행 명령 앞에 붙이는 OS 격리 래퍼 인수 배열 (예: {@code unshare,-rn}). 비어 있으면 OS 격리 없이 실행한다
 * @param retryAfterFailure 시간 초과·엔진 오류 후 다시 변환을 허용하는 간격 (기본 10분)
 * @param cache 캐시 설정
 * @param officeEnabled Office(DOCX/XLSX/PPTX)→PDF 변환 사용 여부 (기본 true). false이면 LibreOffice를 실행하지 않고 503으로
 * 응답한다. PDF 미리보기와 원본 다운로드는 영향 없음 (#27 운영 정책)
 * @param requireIsolation true이면 격리 래퍼({@code launcher})가 없을 때 변환을 끈다 (기본 false, 운영 프로파일 true)
 */
@ConfigurationProperties("docflow.preview")
public record PreviewProperties(Duration timeout, Integer concurrency, Integer queueCapacity, DataSize maxOutputBytes,
		String sofficePath, List<String> launcher, Duration retryAfterFailure, Cache cache, Boolean officeEnabled,
		Boolean requireIsolation) {

	/** 기존 설정(SPEC-05)과 같은 기본값: 변환 사용, 격리 필수 아님. */
	public PreviewProperties(Duration timeout, Integer concurrency, Integer queueCapacity, DataSize maxOutputBytes,
			String sofficePath, List<String> launcher, Duration retryAfterFailure, Cache cache) {
		this(timeout, concurrency, queueCapacity, maxOutputBytes, sofficePath, launcher, retryAfterFailure, cache, null,
				null);
	}

	@ConstructorBinding
	public PreviewProperties {
		officeEnabled = (officeEnabled != null) ? officeEnabled : Boolean.TRUE;
		requireIsolation = (requireIsolation != null) ? requireIsolation : Boolean.FALSE;
		timeout = (timeout != null) ? timeout : Duration.ofSeconds(60);
		concurrency = (concurrency != null) ? concurrency : 1;
		queueCapacity = (queueCapacity != null) ? queueCapacity : 10;
		maxOutputBytes = (maxOutputBytes != null) ? maxOutputBytes : DataSize.ofMegabytes(100);
		sofficePath = (sofficePath != null && !sofficePath.isBlank()) ? sofficePath.strip() : null;
		launcher = (launcher != null) ? launcher.stream().map(String::strip).filter((arg) -> !arg.isEmpty()).toList()
				: List.of();
		retryAfterFailure = (retryAfterFailure != null) ? retryAfterFailure : Duration.ofMinutes(10);
		cache = (cache != null) ? cache : new Cache(null, null, null);
		if (timeout.isNegative() || timeout.isZero() || concurrency < 1 || queueCapacity < 0) {
			throw new IllegalArgumentException("Invalid docflow.preview settings");
		}
		if (cache.maxBytes().toBytes() < maxOutputBytes.toBytes()) {
			throw new IllegalArgumentException(
					"docflow.preview.cache.max-bytes must be >= docflow.preview.max-output-bytes");
		}
	}

	/**
	 * @param maxBytes 전체 캐시 용량 상한 (기본 2GiB)
	 * @param maxIdle 이 기간 사용하지 않은 캐시를 정리한다 (기본 30일)
	 * @param cleanupInterval 미사용 캐시 정리 주기 (기본 1시간)
	 */
	public record Cache(DataSize maxBytes, Duration maxIdle, Duration cleanupInterval) {

		public Cache {
			maxBytes = (maxBytes != null) ? maxBytes : DataSize.ofGigabytes(2);
			maxIdle = (maxIdle != null) ? maxIdle : Duration.ofDays(30);
			cleanupInterval = (cleanupInterval != null) ? cleanupInterval : Duration.ofHours(1);
		}

	}

}
