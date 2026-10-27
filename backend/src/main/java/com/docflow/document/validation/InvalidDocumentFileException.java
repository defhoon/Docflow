package com.docflow.document.validation;

/**
 * 업로드 파일이 형식·구조·파일명 규칙을 만족하지 않는다. {@code 400 VALIDATION_FAILED}, {@code fieldErrors[file]}로
 * 응답한다 (SPEC-03 결정 D6). 메시지는 사용자에게 보여 줄 문구이며 파일명·경로를 포함하지 않는다.
 */
public class InvalidDocumentFileException extends RuntimeException {

	public static final String MALFORMED = "파일이 손상되었거나 형식을 확인할 수 없습니다.";

	public InvalidDocumentFileException(String message) {
		super(message);
	}

}
