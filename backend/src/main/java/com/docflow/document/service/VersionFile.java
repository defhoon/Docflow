package com.docflow.document.service;

import java.io.InputStream;

/**
 * 응답으로 내보낼 버전 파일 (SPEC-05 §6.4, §6.5). 스트림은 응답 작성이 끝나면 닫힌다. 서버 경로·저장 키는 담지 않는다.
 *
 * @param downloadFilename 응답 {@code Content-Disposition}에 쓸 파일명
 * @param contentType 서버가 판별한 Content Type
 * @param sizeBytes 실제 전송 크기
 * @param content 열린 읽기 스트림
 */
public record VersionFile(String downloadFilename, String contentType, long sizeBytes, InputStream content) {
}
