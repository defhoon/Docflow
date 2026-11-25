package com.docflow.extraction.domain;

/**
 * 버전별 PDF 텍스트 추출 결과 상태 (SPEC-10 §5). {@link #EXTRACTED}만 본문을 가진다. 나머지는 사용자에게 사유를 안내하는
 * 최종 상태이며 같은 추출기 버전으로 다시 추출하지 않는다.
 */
public enum ExtractionStatus {

	/** 텍스트를 추출했다. */
	EXTRACTED,
	/** PDF는 정상이지만 추출할 텍스트가 없다 (스캔 문서 등, OCR 미지원). */
	NO_TEXT,
	/** 암호가 걸려 있거나 텍스트 추출이 허용되지 않았다. */
	ENCRYPTED,
	/** PDF를 해석할 수 없다. */
	CORRUPTED,
	/** 페이지 수·단어 수·파일 크기 상한을 넘었다. */
	TOO_LARGE,
	/** 처리 시간 상한을 넘었다. */
	TIMEOUT,
	/** PDF가 아니다 (Office 문서 분석은 범위 밖). */
	UNSUPPORTED_TYPE;

	public boolean hasText() {
		return this == EXTRACTED;
	}

}
