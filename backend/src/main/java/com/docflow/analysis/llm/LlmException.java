package com.docflow.analysis.llm;

import com.docflow.analysis.domain.AnalysisFailure;

/**
 * LLM 호출 실패. {@code transientFailure}이면 잠시 뒤 다시 시도할 수 있다(429, 5xx, 시간 초과, 연결 오류). 메시지에 문서
 * 내용이나 API 키를 넣지 않는다.
 */
public class LlmException extends Exception {

	private final AnalysisFailure failure;

	private final boolean transientFailure;

	private final long inputTokens;

	private final long outputTokens;

	public LlmException(AnalysisFailure failure, boolean transientFailure, String message) {
		this(failure, transientFailure, message, 0, 0);
	}

	/** 응답을 받았지만 쓸 수 없는 경우(거부·잘림). 사용한 토큰을 비용에 반영한다. */
	public LlmException(AnalysisFailure failure, boolean transientFailure, String message, long inputTokens,
			long outputTokens) {
		super(message);
		this.failure = failure;
		this.transientFailure = transientFailure;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
	}

	public AnalysisFailure failure() {
		return this.failure;
	}

	public boolean isTransient() {
		return this.transientFailure;
	}

	public long inputTokens() {
		return this.inputTokens;
	}

	public long outputTokens() {
		return this.outputTokens;
	}

}
