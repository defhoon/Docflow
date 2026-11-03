package com.docflow.document.domain;

/**
 * 검증을 통과해 저장소에 배치된 파일의 메타데이터. {@code contentType}은 서버가 판별한 값이다.
 */
public record StoredDocumentFile(String originalFilename, String contentType, long sizeBytes, String storageKey,
		String sha256) {
}
