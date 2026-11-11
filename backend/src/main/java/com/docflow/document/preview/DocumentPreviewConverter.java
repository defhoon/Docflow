package com.docflow.document.preview;

import java.nio.file.Path;
import java.time.Duration;

import com.docflow.document.validation.DocumentFileType;

/**
 * Office 문서 → PDF 변환 엔진 (SPEC-05 §8.1, ADR-0006 결정 1). 구현은 교체할 수 있다(예: 컨테이너 방식).
 */
public interface DocumentPreviewConverter {

	/** 변환 엔진을 사용할 수 있는지 확인한다. 최초 호출 시 자체 점검을 할 수 있다. */
	EngineStatus status();

	/**
	 * {@code input}을 PDF로 변환하고 결과 파일 경로를 돌려준다. 모든 중간 파일은 {@code workDir} 안에 만든다.
	 *
	 * @throws PreviewConversionException 변환 실패·시간 초과·엔진 실행 실패
	 */
	Path convertToPdf(Path input, DocumentFileType type, Path workDir, Duration timeout)
			throws PreviewConversionException;

	/**
	 * @param available 사용 가능 여부
	 * @param reason 사용할 수 없을 때 운영자용 사유 (응답에 노출하지 않는다)
	 */
	record EngineStatus(boolean available, String reason) {

		public static EngineStatus ok() {
			return new EngineStatus(true, null);
		}

		public static EngineStatus unavailable(String reason) {
			return new EngineStatus(false, reason);
		}

	}

}
