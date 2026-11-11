package com.docflow.document.service;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import com.docflow.audit.domain.AuditAction;
import com.docflow.audit.domain.AuditTargetType;
import com.docflow.audit.service.AuditLogService;
import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.domain.StoredDocumentFile;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.storage.FileStorage;
import com.docflow.document.validation.DocumentFileName;
import com.docflow.document.validation.DocumentFileValidator;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * 기존 문서에 새 버전 등록 (SPEC-04 §6.5, §7).
 * <p>
 * 접근·권한·문서 존재·상태를 파일 저장 전에 확인한 뒤 파일을 배치하고, 그 다음 시작한 DB 트랜잭션에서 문서 행을 잠근 뒤
 * 상태를 다시 검증하고 {@code MAX(version_number) + 1}을 할당한다. 문서 상태는 바꾸지 않는다. 원본 파일명은 로그에 남기지
 * 않는다.
 */
@Service
public class DocumentVersionService {

	static final String VERSION_NUMBER_CONSTRAINT = "uk_document_versions_document_version";

	private static final Logger log = LoggerFactory.getLogger(DocumentVersionService.class);

	private final ProjectService projectService;

	private final ProjectPermissionService projectPermissionService;

	private final UserAccountService userAccountService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository documentVersionRepository;

	private final DocumentFileIntake fileIntake;

	private final StoredFileTransaction storedFileTransaction;

	private final AuditLogService auditLogService;

	public DocumentVersionService(ProjectService projectService, ProjectPermissionService projectPermissionService,
			UserAccountService userAccountService, DocumentRepository documentRepository,
			DocumentVersionRepository documentVersionRepository, FileStorage fileStorage,
			DocumentFileValidator documentFileValidator, PlatformTransactionManager transactionManager,
			AuditLogService auditLogService) {
		this.projectService = projectService;
		this.projectPermissionService = projectPermissionService;
		this.userAccountService = userAccountService;
		this.documentRepository = documentRepository;
		this.documentVersionRepository = documentVersionRepository;
		this.fileIntake = new DocumentFileIntake(fileStorage, documentFileValidator);
		this.storedFileTransaction = new StoredFileTransaction(transactionManager, fileStorage);
		this.auditLogService = auditLogService;
	}

	/**
	 * 새 버전을 등록한다. 이 메서드에는 트랜잭션을 두지 않는다 (SPEC-04 §7.3).
	 */
	public DocumentResponse create(Long projectId, Long documentId, Long sessionUserId, MultipartFile file) {
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.projectPermissionService.requireVersionUploader(project, sessionUserId);
		// 사전 확인: 파일을 쓰기 전에 빠르게 실패한다. 최종 판정은 잠금 후 재검증이다.
		Document current = this.documentRepository.findByIdAndProjectId(documentId, projectId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		requireAcceptsNewVersion(current);
		DocumentFileName fileName = DocumentFileName.of(file.getOriginalFilename());

		StoredDocumentFile storedFile = this.fileIntake.store(file, fileName);

		DocumentResponse response = this.storedFileTransaction.execute(storedFile, (status) -> {
			Document document = this.documentRepository.findByIdAndProjectIdForUpdate(documentId, projectId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
			requireAcceptsNewVersion(document);
			int versionNumber = this.documentVersionRepository.findMaxVersionNumber(documentId) + 1;
			User user = this.userAccountService.getReference(sessionUserId);
			DocumentVersion version = insertVersion(projectId, document, versionNumber, storedFile, user);
			document.markNewVersionAdded();
			// 활동 이력은 같은 트랜잭션에서 기록한다 (SPEC-06 FR-2, AC-AUD-02).
			this.auditLogService.record(projectId, sessionUserId, AuditAction.VERSION_CREATED,
					AuditTargetType.DOCUMENT_VERSION, version.getId(), documentId, versionNumber);
			// 응답의 updatedAt이 DB에 기록된 값(flush 시 Auditing이 정한 값)과 같도록 트랜잭션 안에서 flush한다.
			this.documentRepository.flush();
			return DocumentResponse.from(document, version);
		});
		log.info("Document version created: projectId={}, documentId={}, versionNumber={}, storageKey={}", projectId,
				documentId, response.latestVersion().versionNumber(), storedFile.storageKey());
		return response;
	}

	private DocumentVersion insertVersion(Long projectId, Document document, int versionNumber,
			StoredDocumentFile storedFile, User user) {
		try {
			return this.documentVersionRepository.save(DocumentVersion.next(document, versionNumber, storedFile, user));
		}
		catch (DataIntegrityViolationException ex) {
			// 문서 행 잠금으로 정상 경로에서는 발생하지 않는다. 잠금을 거치지 않은 쓰기 등 원인 분석을 위해 남긴다 (결정 V1).
			if (VERSION_NUMBER_CONSTRAINT.equals(constraintName(ex))) {
				log.error(
						"Unexpected version number conflict: projectId={}, documentId={}, versionNumber={}, constraint={}, storageKey={}",
						projectId, document.getId(), versionNumber, VERSION_NUMBER_CONSTRAINT, storedFile.storageKey());
			}
			throw ex;
		}
	}

	private static void requireAcceptsNewVersion(Document document) {
		if (!document.getStatus().allowsNewVersion()) {
			throw new BusinessException(ErrorCode.DOCUMENT_STATUS_CONFLICT);
		}
	}

	private static String constraintName(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

}
