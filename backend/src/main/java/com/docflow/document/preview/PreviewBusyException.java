package com.docflow.document.preview;

import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;

/**
 * 변환 대기열이 가득 참 (SPEC-05 §6.5). {@code 503 DOCUMENT_PREVIEW_UNAVAILABLE}과 {@code Retry-After}로 응답한다.
 */
public class PreviewBusyException extends BusinessException {

	/** 다시 시도까지 권장 대기 시간(초). */
	public static final int RETRY_AFTER_SECONDS = 5;

	public PreviewBusyException() {
		super(ErrorCode.DOCUMENT_PREVIEW_UNAVAILABLE);
	}

}
