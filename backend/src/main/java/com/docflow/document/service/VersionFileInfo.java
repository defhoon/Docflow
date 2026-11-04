package com.docflow.document.service;

import com.docflow.document.validation.DocumentFileType;

/**
 * 접근 확인을 통과한 버전의 파일 메타데이터 (SPEC-05 §6.6 처리 순서). 트랜잭션 밖에서 파일을 열 때 사용한다. 내부용이며 API
 * 응답에 쓰지 않는다.
 */
public record VersionFileInfo(Long projectId, Long documentId, int versionNumber, String originalFilename,
		String contentType, long sizeBytes, String storageKey, DocumentFileType fileType) {
}
