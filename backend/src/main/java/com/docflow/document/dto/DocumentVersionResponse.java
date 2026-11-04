package com.docflow.document.dto;

import java.time.Instant;

import com.docflow.document.domain.DocumentVersion;
import com.docflow.user.domain.User;

/**
 * 버전 히스토리 항목 (SPEC-05 §6.3). 저장 키·서버 경로·SHA-256·Content Type·업로더 이메일·내부 ID는 포함하지 않는다.
 */
public record DocumentVersionResponse(int versionNumber, String originalFilename, long sizeBytes, Uploader uploadedBy,
		Instant uploadedAt, boolean latest) {

	public static DocumentVersionResponse from(DocumentVersion version, boolean latest) {
		return new DocumentVersionResponse(version.getVersionNumber(), version.getOriginalFilename(),
				version.getSizeBytes(), Uploader.from(version.getUploadedBy()), version.getCreatedAt(), latest);
	}

	public record Uploader(Long id, String displayName) {

		static Uploader from(User user) {
			return new Uploader(user.getId(), user.getDisplayName());
		}

	}

}
