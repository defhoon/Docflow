package com.docflow.document.preview;

/**
 * 변환 실패 (SPEC-05 §8.5). 메시지는 운영 로그용이며 API 응답에 노출하지 않는다.
 */
public class PreviewConversionException extends Exception {

	private final Kind kind;

	public PreviewConversionException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public PreviewConversionException(Kind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public Kind kind() {
		return this.kind;
	}

	public enum Kind {

		/** 문서를 변환할 수 없음(손상, 결과 없음, 결과 무효, 크기 초과). 재시작 전까지 다시 시도하지 않는다. */
		FAILED(false),

		/** 시간 제한 초과. 일정 시간 뒤 다시 시도할 수 있다. */
		TIMEOUT(true),

		/** 엔진 실행·입출력 오류. 일정 시간 뒤 다시 시도할 수 있다. */
		ENGINE(true);

		private final boolean retryable;

		Kind(boolean retryable) {
			this.retryable = retryable;
		}

		public boolean retryable() {
			return this.retryable;
		}

	}

}
