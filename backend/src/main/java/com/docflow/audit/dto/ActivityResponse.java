package com.docflow.audit.dto;

import java.time.Instant;

import com.docflow.audit.domain.AuditAction;

/**
 * 활동 이력 응답 (SPEC-06 §6). 행위자는 표시 이름만, 문서는 ID·제목만 포함한다(storageKey·이메일 미포함, FR-7).
 */
public record ActivityResponse(Long id, AuditAction action, Actor actor, ProjectRef project, DocumentRef document,
		Integer versionNumber, Instant createdAt) {

	public record Actor(Long id, String displayName) {
	}

	public record ProjectRef(Long id, String name) {
	}

	public record DocumentRef(Long id, String title) {
	}

	/** JPQL 생성자 프로젝션용. 문서가 없는 이력은 {@code documentId}가 null이다. */
	public ActivityResponse(Long id, AuditAction action, Long actorId, String actorName, Long projectId,
			String projectName, Long documentId, String documentTitle, Integer versionNumber, Instant createdAt) {
		this(id, action, new Actor(actorId, actorName), new ProjectRef(projectId, projectName),
				documentId == null ? null : new DocumentRef(documentId, documentTitle), versionNumber, createdAt);
	}

}
