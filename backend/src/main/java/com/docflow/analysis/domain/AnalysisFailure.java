package com.docflow.analysis.domain;

/**
 * 분석 실패 사유 (SPEC-11 §5.3). {@code retryable}이면 사용자가 다시 요청할 수 있다. 문서 자체 때문에 실패한 경우(텍스트 없음
 * 등)는 다시 요청해도 결과가 같으므로 막는다.
 */
public enum AnalysisFailure {

	AI_DISABLED(true),
	AI_NOT_CONFIGURED(true),
	QUOTA_EXCEEDED(true),
	AI_API_ERROR(true),
	AI_OUTPUT_INVALID(true),
	AI_REFUSED(false),
	FILE_UNAVAILABLE(true),
	TEXT_TIMEOUT(true),
	TEXT_NO_TEXT(false),
	TEXT_ENCRYPTED(false),
	TEXT_CORRUPTED(false),
	TEXT_TOO_LARGE(false),
	UNSUPPORTED_TYPE(false),
	DOCUMENT_TOO_LARGE(false),
	COMPARE_TOO_LARGE(false),
	VERSION_NOT_FOUND(false);

	private final boolean retryable;

	AnalysisFailure(boolean retryable) {
		this.retryable = retryable;
	}

	public boolean retryable() {
		return this.retryable;
	}

	public static boolean isRetryable(String code) {
		if (code == null) {
			return false;
		}
		try {
			return valueOf(code).retryable;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

}
