package com.docflow.document.validation;

import java.util.Locale;
import java.util.Optional;

/**
 * 허용 파일 형식 (SPEC-03 §5.5, 결정 Q4). OOXML은 주 문서 파트와 그 Content Type을 함께 정의한다.
 */
public enum DocumentFileType {

	PDF("pdf", "application/pdf", null, null),

	DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "/word/document.xml",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"),

	XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "/xl/workbook.xml",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"),

	PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "/ppt/presentation.xml",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml");

	private final String extension;

	private final String contentType;

	private final String mainPartName;

	private final String mainPartContentType;

	DocumentFileType(String extension, String contentType, String mainPartName, String mainPartContentType) {
		this.extension = extension;
		this.contentType = contentType;
		this.mainPartName = mainPartName;
		this.mainPartContentType = mainPartContentType;
	}

	public static Optional<DocumentFileType> fromExtension(String extension) {
		String normalized = extension.toLowerCase(Locale.ROOT);
		for (DocumentFileType type : values()) {
			if (type.extension.equals(normalized)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}

	/** 저장된 Content Type으로 형식을 찾는다 (SPEC-05 미리보기). 서버가 저장한 값만 넘긴다. */
	public static Optional<DocumentFileType> fromContentType(String contentType) {
		for (DocumentFileType type : values()) {
			if (type.contentType.equals(contentType)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}

	/** 소문자 확장자 (점 없음). */
	public String extension() {
		return this.extension;
	}

	public boolean isOoxml() {
		return this.mainPartName != null;
	}

	/** 서버가 판별해 저장하는 Content Type. */
	public String contentType() {
		return this.contentType;
	}

	/** OPC 파트 이름 (예: {@code /word/document.xml}). */
	public String mainPartName() {
		return this.mainPartName;
	}

	public String mainPartContentType() {
		return this.mainPartContentType;
	}

}
