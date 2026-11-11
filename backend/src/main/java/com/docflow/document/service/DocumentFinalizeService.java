package com.docflow.document.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.audit.domain.AuditAction;
import com.docflow.audit.domain.AuditTargetType;
import com.docflow.audit.service.AuditLogService;
import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;
import com.docflow.user.service.UserAccountService;

/**
 * 최종 버전 확정 (SPEC-07 FR-4).
 * <p>
 * 하나의 트랜잭션에서 문서 행을 {@code FOR UPDATE}로 잠근 뒤 상태를 확인한다. 새 버전 등록(SPEC-04)과 피드백 작성(SPEC-08)도
 * 같은 행을 잠그고 상태를 재확인하므로 세 작업은 문서 단위로 직렬화된다(FR-5).
 */
@Service
public class DocumentFinalizeService {

	private static final Logger log = LoggerFactory.getLogger(DocumentFinalizeService.class);

	private final ProjectService projectService;

	private final ProjectPermissionService projectPermissionService;

	private final UserAccountService userAccountService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository documentVersionRepository;

	private final AuditLogService auditLogService;

	public DocumentFinalizeService(ProjectService projectService, ProjectPermissionService projectPermissionService,
			UserAccountService userAccountService, DocumentRepository documentRepository,
			DocumentVersionRepository documentVersionRepository, AuditLogService auditLogService) {
		this.projectService = projectService;
		this.projectPermissionService = projectPermissionService;
		this.userAccountService = userAccountService;
		this.documentRepository = documentRepository;
		this.documentVersionRepository = documentVersionRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public DocumentResponse finalizeDocument(Long projectId, Long documentId, Long sessionUserId, int versionNumber) {
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		Document document = this.documentRepository.findByIdAndProjectIdForUpdate(documentId, projectId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		this.projectPermissionService.requireDocumentFinalizer(project, sessionUserId);
		if (!document.getStatus().isCollaborationOpen()) {
			throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_FINALIZED);
		}
		DocumentVersion version = this.documentVersionRepository
			.findByDocumentIdAndVersionNumber(documentId, versionNumber)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND));
		document.finalizeWith(version, this.userAccountService.getReference(sessionUserId), Instant.now());
		this.auditLogService.record(projectId, sessionUserId, AuditAction.DOCUMENT_FINALIZED, AuditTargetType.DOCUMENT,
				documentId, documentId, versionNumber);
		// 응답의 updatedAt이 DB에 기록된 값과 같도록 트랜잭션 안에서 flush한다.
		this.documentRepository.flush();
		DocumentVersion latest = this.documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
			.orElseThrow(() -> new IllegalStateException("Document has no version: documentId=" + documentId));
		log.info("Document finalized: projectId={}, documentId={}, versionNumber={}", projectId, documentId,
				versionNumber);
		return DocumentResponse.from(document, latest);
	}

}
