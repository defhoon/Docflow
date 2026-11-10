package com.docflow.document.dto;

import java.time.Instant;

import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentStatus;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.user.domain.User;

/**
 * 문서 등록·상세 응답 (SPEC-03 §6.3). 저장 키·서버 경로·해시·Content Type·작성자 이메일은 포함하지 않는다.
 */
public record DocumentResponse(Long id, String title, String description, DocumentStatus status, Author createdBy,
		Instant createdAt, Instant updatedAt, LatestVersion latestVersion, Finalization finalization) {

	public static DocumentResponse from(Document document, DocumentVersion latestVersion) {
		return new DocumentResponse(document.getId(), document.getTitle(), document.getDescription(),
				document.getStatus(), Author.from(document.getCreatedBy()), document.getCreatedAt(),
				document.getUpdatedAt(), LatestVersion.from(latestVersion), Finalization.from(document));
	}

	public record Author(Long id, String displayName) {

		static Author from(User user) {
			return new Author(user.getId(), user.getDisplayName());
		}

	}

	/** 최종 확정 정보 (SPEC-07 FR-7). ACTIVE 문서는 null. */
	public record Finalization(int versionNumber, Author finalizedBy, Instant finalizedAt) {

		static Finalization from(Document document) {
			if (document.getFinalVersion() == null) {
				return null;
			}
			return new Finalization(document.getFinalVersion().getVersionNumber(),
					Author.from(document.getFinalizedBy()), document.getFinalizedAt());
		}

	}

	public record LatestVersion(int versionNumber, String originalFilename, long sizeBytes, Instant uploadedAt) {

		static LatestVersion from(DocumentVersion version) {
			return new LatestVersion(version.getVersionNumber(), version.getOriginalFilename(), version.getSizeBytes(),
					version.getCreatedAt());
		}

	}

}
