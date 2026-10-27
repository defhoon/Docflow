package com.docflow.document.validation;

import com.docflow.project.domain.ProjectText;

/**
 * 원본 파일명 정규화·검증 (SPEC-03 §5.7, 결정 D7). 정규화된 이름만 저장·표시하며 저장 경로에는 쓰지 않는다.
 */
public record DocumentFileName(String value, DocumentFileType type) {

	public static final int MAX_LENGTH = 255;

	static final String UNSUPPORTED_TYPE = "PDF, DOCX, XLSX, PPTX 파일만 업로드할 수 있습니다.";

	static final String INVALID_NAME = "파일 이름을 확인할 수 없거나 사용할 수 없는 문자가 포함되어 있습니다.";

	static final String TOO_LONG = "파일 이름은 255자 이하여야 합니다.";

	/**
	 * 경로 구분자 뒤의 이름만 쓰고, 가장자리 공백을 제거하고, [기존] {@link ProjectText} 이름 문자 규칙(제어·줄 구분·폭
	 * 없는·양방향 제어 문자 거부)을 적용한다. 255자(UTF-16)를 넘으면 자르지 않고 거부한다. 마지막 확장자가 허용 목록에
	 * 있어야 한다.
	 */
	public static DocumentFileName of(String submittedName) {
		if (submittedName == null) {
			throw new InvalidDocumentFileException(INVALID_NAME);
		}
		int separator = Math.max(submittedName.lastIndexOf('/'), submittedName.lastIndexOf('\\'));
		String name = ProjectText.trimEdges(submittedName.substring(separator + 1));
		if (name.isEmpty() || name.equals(".") || name.equals("..") || !ProjectText.isAllowedName(name)) {
			throw new InvalidDocumentFileException(INVALID_NAME);
		}
		if (name.length() > MAX_LENGTH) {
			throw new InvalidDocumentFileException(TOO_LONG);
		}
		int dot = name.lastIndexOf('.');
		if (dot <= 0 || dot == name.length() - 1) {
			throw new InvalidDocumentFileException(UNSUPPORTED_TYPE);
		}
		DocumentFileType type = DocumentFileType.fromExtension(name.substring(dot + 1))
			.orElseThrow(() -> new InvalidDocumentFileException(UNSUPPORTED_TYPE));
		return new DocumentFileName(name, type);
	}

}
