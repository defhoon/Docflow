package com.docflow.document.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * 새 버전 등록 요청 ({@code multipart/form-data}, SPEC-04 §6.3). 파일만 받는다. 버전 번호·업로더·상태 등 정의되지 않은
 * 파트는 바인딩하지 않으므로 무시된다. 파일 크기·형식·파일명은 Service에서 검증한다.
 */
public record CreateDocumentVersionRequest(@NotNull(message = "파일을 선택해 주세요.") MultipartFile file) {
}
